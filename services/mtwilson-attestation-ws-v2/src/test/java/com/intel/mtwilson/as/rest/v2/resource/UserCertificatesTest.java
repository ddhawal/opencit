/*
 * Copyright (C) 2014 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.as.rest.v2.resource;

import com.intel.dcsg.cpg.crypto.RsaUtil;
import com.intel.dcsg.cpg.x509.X509Builder;
import com.intel.dcsg.cpg.x509.X509Util;
import com.intel.mtwilson.My;
import com.intel.mtwilson.as.rest.v2.model.UserCertificate;
import com.intel.mtwilson.ms.controller.ApiClientX509JpaController;
import com.intel.mtwilson.ms.data.ApiClientX509;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

/**
 *
 * @author jbuhacoff
 */
public class UserCertificatesTest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserCertificatesTest.class);
    
    @Test
    public void testStoreUserCertificateWithJpa() throws Exception {
        ApiClientX509JpaController userCertJpaController = My.jpa().mwApiClientX509();
        ApiClientX509 user = userCertJpaController.findApiClientX509ByUUID("7f2a647d-8172-44a6-b15a-30eaa42580e7");            
        // create a new key and certificate for the same subject name
        KeyPair keypair = RsaUtil.generateRsaKeyPair(1024);
        X509Certificate certificate = X509Builder.factory().selfSigned(user.getName(), keypair).build();
        user.setCertificate(certificate.getEncoded());
        userCertJpaController.edit(user);
    }
    
    @Test
    public void testStoreUserCertificate() throws Exception {
        UserCertificates userCertificates = new UserCertificates();
        UserCertificate userCertificate = userCertificates.retrieve("7f2a647d-8172-44a6-b15a-30eaa42580e7");
        // create a new key and certificate for the same subject name
        KeyPair keypair = RsaUtil.generateRsaKeyPair(1024);
        X509Certificate certificate = X509Builder.factory().selfSigned(userCertificate.getName(), keypair).build();
        userCertificate.setCertificate(certificate.getEncoded());
        log.debug("Certificate is {} bytes", userCertificate.getCertificate().length);
        userCertificates.store(userCertificate);
    }
    
    @Test
    public void testRetrieveUserCertificate() throws IOException, CertificateException {
        log.debug("mtwilson.db.host = {} configured in {}", My.configuration().getDatabaseHost(), My.configuration().getSource("mtwilson.db.host"));
        log.debug("mtwilson.db.port = {} configured in {}", My.configuration().getDatabasePort(), My.configuration().getSource("mtwilson.db.port"));
        log.debug("mtwilson.db.user = {} configured in {}", My.configuration().getDatabaseUsername(), My.configuration().getSource("mtwilson.db.user"));
        log.debug("mtwilson.db.password = {} configured in {}", My.configuration().getDatabasePassword(), My.configuration().getSource("mtwilson.db.password"));
        log.debug("mtwilson.db.driver = {} configured in {}", My.configuration().getDatabaseDriver(), My.configuration().getSource("mtwilson.db.driver"));
        log.debug("mtwilson.db.schema = {} configured in {}", My.configuration().getDatabaseSchema(), My.configuration().getSource("mtwilson.db.schema"));
        log.debug("jdbc url = {}", My.jdbc().url());
        UserCertificates userCertificates = new UserCertificates();
        UserCertificate userCertificate = userCertificates.retrieve("7f2a647d-8172-44a6-b15a-30eaa42580e7");
        log.debug("Retrieved user certificate: {}", userCertificate.getName());
        log.debug("Certificate is {} bytes", userCertificate.getCertificate().length);
        FileOutputStream out = new FileOutputStream(new File("target/test.crt"));
        IOUtils.write(userCertificate.getCertificate(), out);
        out.close();
//        log.debug("Certificate bytes: {}", userCertificate.getCertificate());
//        log.debug("Certificate bytes: {}", new String(userCertificate.getCertificate()));
//        X509Util.decodeDerCertificate(userCertificate.getCertificate()); // empty input exception from x509 engine ... 
        
        X509Certificate x509 = userCertificate.getX509Certificate();
        log.debug("Cetificate subject: {}", x509.getSubjectX500Principal().getName());
        
    }
    
    @Test
    public void testBase64() {
        String test = new String(new byte[] {84, 52, 57, 52, 57, 52, 51, 52, 55, 53, 52, 52, 51, 52, 51, 52, 49, 53, 57}); // ...  52, 98, 54, 55, 52, 49, 55, 55, 52, 57, 52, 50, 52, 49, 54, 55, 52, 57, 52, 57, 52, 57, 50, 98,
        log.debug(test);
    }
}