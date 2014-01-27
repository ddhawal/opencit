/*
 * Copyright (C) 2013 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.setup.tasks;

import com.intel.dcsg.cpg.crypto.RsaCredentialX509;
import com.intel.dcsg.cpg.crypto.RsaUtil;
import com.intel.dcsg.cpg.crypto.SimpleKeystore;
import com.intel.dcsg.cpg.validation.Fault;
import com.intel.dcsg.cpg.x509.X509Builder;
import com.intel.dcsg.cpg.x509.X509Util;
import com.intel.mtwilson.My;
import com.intel.mtwilson.setup.LocalSetupTask;
import com.intel.mtwilson.setup.SetupException;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import org.apache.commons.io.IOUtils;

/**
 * Depends on CreateCertificateAuthorityKey to create the cakey first
 * 
 * @author jbuhacoff
 */
public class CreateTlsCertificate extends LocalSetupTask {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CreateTlsCertificate.class);

    private String tlsDistinguishedName = "CN=mtwilson-tls,OU=mtwilson";
    private String ipAlternativeName = "127.0.0.1"; // XXX TODO we need to provide a configuration setting for this ... it may already be in the mtwilson.env and just need to formalize it in My.configuration()
    private String dnsAlternativeName = "localhost";
//    private File tlsKeystoreFile = null; // maybe the path would be a configuration item, currently it's hardcoded to be "mtwilson-tls.jks" under MTWILSON_CONF
    private String tlsKeystorePassword = null;
    
    @Override
    protected void configure() throws Exception {
        if(tlsDistinguishedName == null) {
            configuration("TLS distinguished name is not configured");
        }
        if( ipAlternativeName == null && dnsAlternativeName == null ) {
            configuration("No alternative name is configured; set IP, DNS, or both");
        }
        tlsKeystorePassword = My.configuration().getTlsKeystorePassword();
        if( tlsKeystorePassword == null ) {
            // XXX TODO automatically generate tlsKeystorePassword 
            // after configure() the application should  getTlsKeystorePassword()  from us and 
            // save it (for example in mtwilson.properties)
            tlsKeystorePassword ="password";
        }
        
        // this section about checkign the ca key availability
        // is in configuration because it must be ready before the
        // setup task can even run
        // it's copied from the validate() method of CreateCertificateAuthorityKe
        // and probably this code needs to be refactored so we don't repeat it;
        // the challenge is whether the exception handling with configuration/validation
        // fault logging can be refactored because the CA setup needs to log them
        // as validation issues while dependent setups such as this TLS setup need to 
        // log them as configuration issues here
            byte[] combinedPrivateKeyAndCertPemBytes;
            FileInputStream cakeyIn = new FileInputStream(My.configuration().getCaKeystoreFile()); // throws FileNotFoundException, IOException
            combinedPrivateKeyAndCertPemBytes = IOUtils.toByteArray(cakeyIn); // throws IOException
            cakeyIn.close();
            try {
                PrivateKey cakey = RsaUtil.decodePemPrivateKey(new String(combinedPrivateKeyAndCertPemBytes));
                log.debug("Read cakey {} from {}", cakey.getAlgorithm(), My.configuration().getCaKeystoreFile().getAbsolutePath());
            }
            catch(Exception e) {
                log.debug("Cannot read private key from {}", My.configuration().getCaKeystoreFile().getAbsolutePath(), e);
                configuration("Cannot read private key from: %s", My.configuration().getCaKeystoreFile().getAbsolutePath());
            }
            try {
                X509Certificate cacert = X509Util.decodePemCertificate(new String(combinedPrivateKeyAndCertPemBytes));
                log.debug("Read cacert {} from {}", cacert.getSubjectX500Principal().getName(), My.configuration().getCaKeystoreFile().getAbsolutePath());
            }
            catch(Exception e) {
                log.debug("Cannot read certificate from {}", My.configuration().getCaKeystoreFile().getAbsolutePath(), e);
                configuration("Cannot read certificate from: %s", My.configuration().getCaKeystoreFile().getAbsolutePath());
            }
        
    }

    @Override
    protected void validate() throws Exception {
        File tlsKeystoreFile = My.configuration().getTlsKeystoreFile();
        if( !tlsKeystoreFile.exists() ) {
            validation("TLS keystore is missing");
        }
        // keystore exists, look for the private key and cert
        // XXX TODO make sure it has a TLS private key and certificate inside
        if( tlsKeystorePassword == null ) {
            configuration("TLS keystore password is not configured");
            return;
        }
        SimpleKeystore keystore = new SimpleKeystore(tlsKeystoreFile, tlsKeystorePassword);
        for(String alias : keystore.aliases()) {
            log.debug("Keystore alias: {}", alias);
            // make sure it has a TLS private key and certificate inside
            try {
                RsaCredentialX509 credential = keystore.getRsaCredentialX509(alias, tlsKeystorePassword);
                log.debug("TLS certificate: {}", credential.getCertificate().getSubjectX500Principal().getName());
            }
            catch(Exception e) {
                log.debug("Cannot read TLS key from keystore", e);
//                validation("Cannot read TLS key from keystore"); // we are assuming the keystore only has one private key entry ... 
            }
        }
    }

    @Override
    protected void execute() throws Exception {
        // load the ca key - same code as in configure() but without exception
        // handling 
            byte[] combinedPrivateKeyAndCertPemBytes;
            FileInputStream cakeyIn = new FileInputStream(My.configuration().getCaKeystoreFile()); // throws FileNotFoundException, IOException
            combinedPrivateKeyAndCertPemBytes = IOUtils.toByteArray(cakeyIn); // throws IOException
            cakeyIn.close();
            PrivateKey cakey = RsaUtil.decodePemPrivateKey(new String(combinedPrivateKeyAndCertPemBytes));
            X509Certificate cacert = X509Util.decodePemCertificate(new String(combinedPrivateKeyAndCertPemBytes));
        
        // create a new key pair for TLS
        KeyPair tlskey = RsaUtil.generateRsaKeyPair(2048);
        X509Builder builder = X509Builder.factory();
//        builder.selfSigned(tlsDistinguishedName, tlskey);
        builder.issuerName(cacert);
        builder.issuerPrivateKey(cakey);
        builder.subjectName(tlsDistinguishedName);
        builder.subjectPublicKey(tlskey.getPublic());
        if( dnsAlternativeName != null ) {
            builder.dnsAlternativeName(dnsAlternativeName);
        }
        if( ipAlternativeName != null ) {
            builder.ipAlternativeName(ipAlternativeName);            
        }
        X509Certificate tlscert = builder.build();
        if( cacert == null ) {
//            log.error("Failed to create certificate"); // no need to print this, if the build failed there are guaranteed to be faults to print...
            List<Fault> faults = builder.getFaults();
            for(Fault fault : faults) {
                log.error(String.format("%s%s", fault.toString(), fault.getCause() == null ? "" : ": "+fault.getCause().getMessage()));
                validation(fault); // XXX TODO  should we have an execution() category of faults? 
            }
            throw new SetupException("Cannot generate TLS certificate");
            
        }
        
        File tlsKeystoreFile = My.configuration().getTlsKeystoreFile();
        SimpleKeystore keystore = new SimpleKeystore(tlsKeystoreFile, tlsKeystorePassword);
//        keystore.addTrustedCaCertificate(cacert, cacert.getIssuerX500Principal().getName());
        keystore.addKeyPairX509(tlskey.getPrivate(), tlscert, tlsDistinguishedName, tlsKeystorePassword, cacert); // we have to provide the issuer chain since it's not self-signed,  otherwise we'll get an exception from the KeyStore provider
        keystore.save();
    }
    
}
