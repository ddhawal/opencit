package com.intel.mtwilson.agent.intel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBException;
import com.intel.mtwilson.agent.*;
import com.intel.mtwilson.tls.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.IOUtils;

import com.intel.mountwilson.as.common.ASConfig;
import com.intel.mountwilson.as.common.ASException;
import com.intel.mountwilson.as.helper.CommandUtil;
import com.intel.mountwilson.as.helper.TrustAgentSecureClient;
import com.intel.mountwilson.ta.data.ClientRequestType;
import com.intel.mountwilson.ta.data.daa.response.DaaResponse;
import com.intel.mtwilson.as.data.TblHosts;
import com.intel.mtwilson.crypto.X509Util;
import com.intel.mtwilson.datatypes.ErrorCode;
import com.intel.mtwilson.model.Pcr;
import com.intel.mtwilson.model.PcrIndex;
import com.intel.mtwilson.model.PcrManifest;
import com.intel.mtwilson.model.Sha1Digest;
import java.io.StringWriter;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In order to use the TAHelper, you need to have attestation-service.properties on your machine.
 * 
 * Here are example properties that Jonathan has at C:/Intel/CloudSecurity/attestation-service.properties:
 * 
com.intel.mountwilson.as.home=C:/Intel/CloudSecurity/AttestationServiceData/aikverifyhome
com.intel.mountwilson.as.aikqverify.cmd=aikqverify.exe
com.intel.mountwilson.as.openssl.cmd=openssl.bat
 * 
 * The corresponding files must exist. From the above example:
 * 
 *    C:/Intel/CloudSecurity/AttestationServiceData/aikverifyhome
 *    C:/Intel/CloudSecurity/AttestationServiceData/aikverifyhome/data   (can be empty, TAHelper will save files there)
 *    C:/Intel/CloudSecurity/AttestationServiceData/aikverifyhome/bin
 *         contains:  aikqverify.exe, cygwin1.dll
 * 
 * @author dsmagadx
 */
public class TAHelper {
    private Logger log = LoggerFactory.getLogger(getClass());

    private String aikverifyhome;
    private String aikverifyhomeData;
    private String aikverifyhomeBin;
//    private String opensslCmd;
    private String aikverifyCmd;
    
    private Pattern pcrNumberPattern = Pattern.compile("[0-9]|[0-1][0-9]|2[0-3]"); // integer 0-23 with optional zero-padding (00, 01, ...)
    private Pattern pcrValuePattern = Pattern.compile("[0-9a-fA-F]{40}"); // 40-character hex string
    private String pcrNumberUntaint = "[^0-9]";
    private String pcrValueUntaint = "[^0-9a-fA-F]";
//	private EntityManagerFactory entityManagerFactory;
    
    private String trustedAik = null; // host's AIK in PEM format, for use in verifying quotes (caller retrieves it from database and provides it to us)
    
    public TAHelper(/*EntityManagerFactory entityManagerFactory*/) {
        Configuration config = ASConfig.getConfiguration();
        aikverifyhome = config.getString("com.intel.mountwilson.as.home", "C:/work/aikverifyhome");
        aikverifyhomeData = aikverifyhome+File.separator+"data";
        aikverifyhomeBin = aikverifyhome+File.separator+"bin";
//        opensslCmd = aikverifyhomeBin + File.separator + config.getString("com.intel.mountwilson.as.openssl.cmd", "openssl.bat");  // 20130409 removing openssl.bat/openssl.sh requirement because it was used in the mtwilson prototype to extract the RSA Public Key from the X509 Certificate;  we now have convenient facilities to do that from our Java code directly
        aikverifyCmd = aikverifyhomeBin + File.separator + config.getString("com.intel.mountwilson.as.aikqverify.cmd", "aikqverify.exe");
        
        boolean foundAllRequiredFiles = true;
        String required[] = new String[] { aikverifyhome, aikverifyCmd, aikverifyhomeData };
        for(String filename : required) {
            File file = new File(filename);
            if( !file.exists() ) {
                log.error( String.format("Invalid service configuration: Cannot find %s", filename ));
                foundAllRequiredFiles = false;
            }
        }
        if( !foundAllRequiredFiles ) {
            throw new ASException(ErrorCode.AS_CONFIGURATION_ERROR, "Cannot find aikverify files");
        }
        
        // we must be able to write to the data folder in order to save certificates, nones, public keys, etc.
        File datafolder = new File(aikverifyhomeData);
        if( !datafolder.canWrite() ) {
            throw new ASException(ErrorCode.AS_CONFIGURATION_ERROR, String.format(" Cannot write to %s", aikverifyhomeData));            
        }
        
//        this.setEntityManagerFactory(entityManagerFactory);
    }
    
    public void setTrustedAik(String pem) {
        trustedAik = pem;
    }

    // DAA challenge
//    public void verifyAikWithDaa(String hostIpAddress, int port) {
    public void verifyAikWithDaa(TblHosts tblHosts) {
        try {
//            TrustAgentSecureClient client = new TrustAgentSecureClient(hostIpAddress, port); // bug #497 TODO need to replace with use of HostAgentFactory
              HostAgentFactory factory = new HostAgentFactory();
              TlsPolicy tlsPolicy = factory.getTlsPolicy(tblHosts);
        String connectionString = tblHosts.getAddOnConnectionInfo();
        if( connectionString == null || connectionString.isEmpty() ) {
            if( tblHosts.getIPAddress() != null  ) {
                connectionString = String.format("https://%s:%d", tblHosts.getIPAddress(), tblHosts.getPort()); // without vendor scheme because we are passing directly to TrustAgentSEcureClient  (instead of to HOstAgentFactory)
            }
        }
              
            TrustAgentSecureClient client = new TrustAgentSecureClient(new TlsConnection(connectionString, tlsPolicy));
            
            String sessionId = generateSessionId();

            // request AIK certificate and CA chain (the AIK Proof File)
            System.out.println("DAA requesting AIK proof");
            String aikproof = client.getAIKCertificate(); // <identity_request></identity_request>
            FileOutputStream outAikProof = new FileOutputStream(new File(getDaaAikProofFileName(sessionId)));
            IOUtils.write(aikproof, outAikProof);
            IOUtils.closeQuietly(outAikProof);
            
            // TODO: verify issuer chain for the certificate so we can attest to the hardware if we recognize the manufacturer
            
            // create DAA challenge secret
            SecureRandom random = new SecureRandom();
            byte[] secret = new byte[20];
            random.nextBytes(secret);
            FileOutputStream outSecret = new FileOutputStream(new File(getDaaSecretFileName(sessionId)));
            IOUtils.write(secret, outSecret);
            IOUtils.closeQuietly(outSecret);
            
            // encrypt DAA challenge secret using AIK public key so only TPM can read it
            CommandUtil.runCommand(String.format("aikchallenge %s %s %s %s", 
                    getDaaSecretFileName(sessionId), 
                    getDaaAikProofFileName(sessionId), 
                    getDaaChallengeFileName(sessionId), 
                    getRSAPubkeyFileName(sessionId)), false, "Aik Challenge");
            
            // send DAA challenge to Trust Agent and validate the response
            FileInputStream in = new FileInputStream(new File(getDaaChallengeFileName(sessionId)));
            String challenge = IOUtils.toString(in);
            IOUtils.closeQuietly(in);
            DaaResponse response = client.sendDaaChallenge(challenge);
            byte[] responseContentDecoded = Base64.decodeBase64(response.getContent());
            if( responseContentDecoded.length != secret.length ) {
                throw new ASException(ErrorCode.AS_TRUST_AGENT_DAA_ERROR, "Incorrect challenge response");                
            }
            for(int i=0; i<secret.length; i++) {
                if( responseContentDecoded[i] != secret[i] ) {
                    throw new ASException(ErrorCode.AS_TRUST_AGENT_DAA_ERROR, "Incorrect challenge response");                        
                }
            }
           
            // TODO: Trust Agent is validated so now save the AIK certificate and RSA public key in the DATABASE ... 
            
        } catch (KeyManagementException ex) {
            log.error("Cannot verify AIK: "+ex.getMessage(), ex);
        } catch (UnknownHostException ex) {
            log.error("Cannot verify AIK: "+ex.getMessage(), ex);
        } catch (JAXBException ex) {
            log.error("Cannot verify AIK: "+ex.getMessage(), ex);
        } catch (IOException ex) {
            log.error("Cannot verify AIK: "+ex.getMessage(), ex);
        } catch (NoSuchAlgorithmException ex) {
            log.error("Cannot verify AIK: "+ex.getMessage(), ex);
        } catch (ASException ex) {
            throw ex;
        }
    }
    
    // BUG #497 see  the other getQuoteInformationForHost which is called from IntelHostAgent
//    public HashMap<String, PcrManifest> getQuoteInformationForHost(String hostIpAddress, String pcrList, String name, int port) {
    public PcrManifest getQuoteInformationForHost(TblHosts tblHosts) {
            
          try {
              // going to IntelHostAgent directly because 1) we are TAHelper so we know we need intel trust agents,  2) the HostAgent interface isn't ready yet for full generic usage,  3) one day this entire function will be in the IntelHostAgent or that agent will call THIS function instaed of the othe way around
              HostAgentFactory factory = new HostAgentFactory();
              TlsPolicy tlsPolicy = factory.getTlsPolicy(tblHosts.getTlsPolicyName(), tblHosts.getTlsKeystoreResource());
              
        String connectionString = tblHosts.getAddOnConnectionInfo();
        if( connectionString == null || connectionString.isEmpty() ) {
            if( tblHosts.getIPAddress() != null  ) {
                connectionString = String.format("https://%s:%d", tblHosts.getIPAddress(), tblHosts.getPort()); // without vendor scheme because we are passing directly to TrustAgentSEcureClient  (instead of to HOstAgentFactory)
                log.debug("getQuoteInformationForHost called with ip address and port {}", connectionString);
            }
        }
        else if( connectionString.startsWith("intel:") ) {
            log.debug("getQuoteInformationForHost called with intel connection string: {}", connectionString);
            connectionString = connectionString.substring(6);
        }        
              
              
            TrustAgentSecureClient client = new TrustAgentSecureClient(new TlsConnection(connectionString, tlsPolicy));
//                IntelHostAgent agent = new IntelHostAgent(client, new InternetAddress(tblHosts.getIPAddress().toString()));
                
            
            return  getQuoteInformationForHost( tblHosts.getIPAddress(), client);

            
        } catch (ASException e) {
            throw e;
        } catch(UnknownHostException e) {
            throw new ASException(e,ErrorCode.AS_HOST_COMMUNICATION_ERROR, "Unknown host: "+(tblHosts.getIPAddress()==null?"missing IP Address":tblHosts.getIPAddress().toString()));
        }  catch (Exception e) {
            throw new ASException(e);
        }
    }
    
    public PcrManifest getQuoteInformationForHost(String hostname, TrustAgentSecureClient client) throws Exception {
              //  XXX BUG #497  START CODE SNIPPET MOVED TO INTEL HOST AGENT   
            String nonce = generateNonce();

            String sessionId = generateSessionId();

            ClientRequestType clientRequestType = client.getQuote(nonce, "0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23"); // pcrList used to be a comma-separated list passed to this method... but now we are returning a quote with ALL the PCR's ALL THE TIME.
            log.info( "got response from server ["+hostname+"] "+clientRequestType);

            String quote = clientRequestType.getQuote();
            log.info( "extracted quote from response: "+quote);

            saveQuote(quote, sessionId);
            log.info( "saved quote with session id: "+sessionId);

            // we only need to save the certificate when registring the host ... when we are just getting a quote we need to verify it using the previously saved AIK.
            if( trustedAik == null ) {
                String aikCertificate = clientRequestType.getAikcert();            
                log.info( "extracted aik cert from response: "+aikCertificate);
            
                saveCertificate(aikCertificate, sessionId); 
                log.info( "saved host-provided AIK certificate with session id: "+sessionId);
            }
            else {
                saveCertificate(trustedAik, sessionId); // XXX we only need to save the certificate when registring the host ... when we are just getting a quote we don't need it            
                log.info( "saved database-provided trusted AIK certificate with session id: "+sessionId);                
            }
            
            saveNonce(nonce,sessionId);
            
            log.info( "saved nonce with session id: "+sessionId);
            
            createRSAKeyFile(sessionId);

            log.info( "created RSA key file for session id: "+sessionId);
            
            PcrManifest pcrManifest = verifyQuoteAndGetPcr(sessionId);
            
            log.info( "Got PCR map");
            //log.log(Level.INFO, "PCR map = "+pcrMap); // need to untaint this first
            
            return pcrManifest;
        
    }

    // hostName == internetAddress.toString() or Hostname.toString() or IPAddress.toString()
    // vmmName == tblHosts.getVmmMleId().getName()
    public String getHostAttestationReport(String hostName, PcrManifest pcrManifest, String vmmName) throws Exception {
        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        XMLStreamWriter xtw;
        StringWriter sw = new StringWriter();
        
        /*
            // We need to check if the host supports TPM or not. Only way we can do it
            // using the host table contents is by looking at the AIK Certificate. Based
            // on this flag we generate the attestation report.
            boolean tpmSupport = true;
            String hostType = "";

            if (tblHosts.getAIKCertificate() == null || tblHosts.getAIKCertificate().isEmpty()) {
                tpmSupport = false;
            }
            * */
        boolean tpmSupport = true;  // XXX   assuming it supports TPM since it's trust agent and we got a pcr manifest (which we only get from getQuoteInformationFromHost if the tpm quote was verified, which means we saved the AIK certificate when we did that)


            // xtw = xof.createXMLStreamWriter(new FileWriter("c:\\temp\\nb_xml.xml"));
            xtw = xof.createXMLStreamWriter(sw);
            xtw.writeStartDocument();
            xtw.writeStartElement("Host_Attestation_Report");
            xtw.writeAttribute("Host_Name", hostName);
            xtw.writeAttribute("Host_VMM", vmmName);
            xtw.writeAttribute("TXT_Support", String.valueOf(tpmSupport));

            if (tpmSupport == true) {
                for(int i=0; i<24; i++) {
//                ArrayList<IManifest> pcrMFList = new ArrayList<IManifest>();
//                pcrMFList.addAll(pcrManifestMap.values());

//                for (IManifest pcrInfo : pcrMFList) {
                    Pcr pcr = pcrManifest.getPcr(i);
//                    PcrManifest pInfo = (PcrManifest) pcrInfo;
                    xtw.writeStartElement("PCRInfo");
                    xtw.writeAttribute("ComponentName", pcr.getIndex().toString()); // String.valueOf(pInfo.getPcrNumber()));
                    xtw.writeAttribute("DigestValue", pcr.getValue().toString().toUpperCase()); // pInfo.getPcrValue().toUpperCase());
                    xtw.writeEndElement();
                }
            } else {
                xtw.writeStartElement("PCRInfo");
                xtw.writeAttribute("Error", "Host does not support TPM.");
                xtw.writeEndElement();
            }

            xtw.writeEndElement();
            xtw.writeEndDocument();
            xtw.flush();
            xtw.close();
            
            String attestationReport = sw.toString();        
            return attestationReport;
    }
    
    public String generateNonce() {
        try {
            // Create a secure random number generator
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            // Get 1024 random bits
            byte[] bytes = new byte[16];
            sr.nextBytes(bytes);

//            nonce = new BASE64Encoder().encode( bytes);
            String nonce = Base64.encodeBase64String(bytes);

            log.info( "Nonce Generated {}", nonce);
            return nonce;
        } catch (NoSuchAlgorithmException e) {
            throw new ASException(e);
        }
    }

    private String generateSessionId() throws NoSuchAlgorithmException  {
        
        // Create a secure random number generator
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            // Get 1024 random bits
            byte[] seed = new byte[1];
            sr.nextBytes(seed);

            sr = SecureRandom.getInstance("SHA1PRNG");
            sr.setSeed(seed);
            
            

            int nextInt = sr.nextInt();
            String sessionId = "" + ((nextInt < 0)?nextInt *-1 :nextInt); 


            log.info( "Session Id Generated [{}]", sessionId);

        

        return sessionId;

    }
    
    // for DAA
    private String getDaaAikProofFileName(String sessionId) {
        return "daaaikproof_"+sessionId+".data";
    }
    private String getDaaSecretFileName(String sessionId) {
        return "daasecret_"+sessionId+".data";
    }
    private String getDaaChallengeFileName(String sessionId) {
        return "daachallenge_"+sessionId+".data";
    }
    /*
    private String getDaaResponseFileName(String sessionId) {
        return "daaresponse_"+sessionId+".data";
    }
    */

    private String getNonceFileName(String sessionId) {
        return "nonce_" + sessionId +".data";
    }

    private String getQuoteFileName(String sessionId) {
        return "quote_" + sessionId +".data";
    }

    private void saveCertificate(String aikCertificate, String sessionId) throws IOException, CertificateException  {
        
        /*
        // XXX this block of code where we fix the PEM format can be replaced with mtwilson-crypto X509Util.encodePemCertificate(X509Util.decodePemCertificate(...input...))
        // first get a consistent newline character
        aikCertificate = aikCertificate.replace('\r', '\n').replace("\n\n", "\n");
        if( aikCertificate.indexOf("-----BEGIN CERTIFICATE-----\n") < 0 && aikCertificate.indexOf("-----BEGIN CERTIFICATE-----") >= 0 ) {
            log.info( "adding newlines to certificate BEGIN tag");            
            aikCertificate = aikCertificate.replace("-----BEGIN CERTIFICATE-----", "-----BEGIN CERTIFICATE-----\n");
        }
        if( aikCertificate.indexOf("\n-----END CERTIFICATE-----") < 0 && aikCertificate.indexOf("-----END CERTIFICATE-----") >= 0 ) {
            log.info( "adding newlines to certificate END tag");            
            aikCertificate = aikCertificate.replace("-----END CERTIFICATE-----", "\n-----END CERTIFICATE-----");
        }

        saveFile(getCertFileName(sessionId), aikCertificate.getBytes());
        */
        
        X509Certificate aikcert = X509Util.decodePemCertificate(aikCertificate);
        String pem = X509Util.encodePemCertificate(aikcert);
        FileOutputStream out = new FileOutputStream(new File(aikverifyhomeData + File.separator + getCertFileName(sessionId)));
        IOUtils.write(pem, out);
        IOUtils.closeQuietly(out);
    }

    private String getCertFileName(String sessionId) {
        return "aikcert_" + sessionId + ".cer";
    }

    private void saveFile(String fileName, byte[] contents) throws IOException  {
        FileOutputStream fileOutputStream = null;

        try {
            assert aikverifyhome != null;
            log.info( String.format("saving file %s to [%s]", fileName, aikverifyhomeData));
            fileOutputStream = new FileOutputStream(aikverifyhomeData + File.separator +fileName);
            assert fileOutputStream != null;
            assert contents != null;
            fileOutputStream.write(contents);
            fileOutputStream.flush();
        }
        catch(FileNotFoundException e) {
            log.info( String.format("cannot save to file %s in [%s]: %s", fileName, aikverifyhomeData, e.getMessage()));
            throw e;
        } finally {
                 try {
                    fileOutputStream.close();
                } catch (IOException ex) {
                    log.error(String.format("Cannot close file %s in [%s]: %s", fileName, aikverifyhomeData, ex.getMessage()), ex);
                }
        }


    }

    private void saveQuote(String quote, String sessionId) throws IOException  {
//          byte[] quoteBytes = new BASE64Decoder().decodeBuffer(quote);
        byte[] quoteBytes = Base64.decodeBase64(quote);
          saveFile(getQuoteFileName(sessionId), quoteBytes);
    }

    private void saveNonce(String nonce, String sessionId) throws IOException  {
//          byte[] nonceBytes = new BASE64Decoder().decodeBuffer(nonce);
        byte[] nonceBytes = Base64.decodeBase64(nonce);
          saveFile(getNonceFileName(sessionId), nonceBytes);
    }

    private void createRSAKeyFile(String sessionId) throws IOException, CertificateException  {
        // 20130409 replacing external openssl command with equivalent java code, see below
        /*
        String command = String.format("%s %s %s",opensslCmd,aikverifyhomeData + File.separator + getCertFileName(sessionId),aikverifyhomeData + File.separator+getRSAPubkeyFileName(sessionId)); 
        log.info( "RSA Key Command {}", command);
        CommandUtil.runCommand(command, false, "CreateRsaKey" );
        //log.log(Level.INFO, "Result - {0} ", result);
        */
        FileInputStream in = new FileInputStream(new File(aikverifyhomeData + File.separator + getCertFileName(sessionId)));
        String x509cert = IOUtils.toString(in);
        IOUtils.closeQuietly(in);
        X509Certificate aikcert = X509Util.decodePemCertificate(x509cert);
        String aikpubkey = X509Util.encodePemPublicKey(aikcert.getPublicKey());
        FileOutputStream out = new FileOutputStream(new File(aikverifyhomeData + File.separator + getRSAPubkeyFileName(sessionId)));
        IOUtils.write(aikpubkey, out);
        IOUtils.closeQuietly(out);
    }

    private String getRSAPubkeyFileName(String sessionId) {
        return "rsapubkey_" + sessionId + ".key";
    }

    // BUG #497 need to rewrite this to return List<Pcr> ... the Pcr.equals()  does same as (actually more than) IManifest.verify() because Pcr ensures the index is the same and IManifest does not!  and also it is less redundant, because this method returns Map< pcr index as string, manifest object containing pcr index and value >  
    private PcrManifest verifyQuoteAndGetPcr(String sessionId) {
//        HashMap<String,PcrManifest> pcrMp = new HashMap<String,PcrManifest>();
        PcrManifest pcrManifest = new PcrManifest();
        log.info( "verifyQuoteAndGetPcr for session {}",sessionId);
        String command = String.format("%s -c %s %s %s",aikverifyCmd, aikverifyhomeData + File.separator+getNonceFileName( sessionId),
                aikverifyhomeData + File.separator+getRSAPubkeyFileName(sessionId),aikverifyhomeData + File.separator+getQuoteFileName(sessionId)); 
        
        log.info( "Command: {}",command);
        List<String> result = CommandUtil.runCommand(command,true,"VerifyQuote");
        
        // Sample output from command:
        //  1 3a3f780f11a4b49969fcaa80cd6e3957c33b2275
        //  17 bfc3ffd7940e9281a3ebfdfa4e0412869a3f55d8
        //log.log(Level.INFO, "Result - {0} ", result); // need to untaint this first
        
        //List<String> pcrs = getPcrsList(); // replaced with regular expression that checks 0-23
        
        for(String pcrString: result){
            String[] parts = pcrString.trim().split(" ");
            if( parts.length == 2 ) {
                String pcrNumber = parts[0].trim().replaceAll(pcrNumberUntaint, "").replaceAll("\n", "");
                String pcrValue = parts[1].trim().replaceAll(pcrValueUntaint, "").replaceAll("\n", "");
                boolean validPcrNumber = pcrNumberPattern.matcher(pcrNumber).matches();
                boolean validPcrValue = pcrValuePattern.matcher(pcrValue).matches();
                if( validPcrNumber && validPcrValue ) {
                	log.info("Result PCR "+pcrNumber+": "+pcrValue);
//                	pcrMp.put(pcrNumber, new PcrManifest(Integer.parseInt(pcrNumber),pcrValue));            	
                    pcrManifest.setPcr(new Pcr(PcrIndex.valueOf(Integer.parseInt(pcrNumber)), new Sha1Digest(pcrValue)));
                }            	
            }
            else {
            	log.warn( "Result PCR invalid");
            }
            /*
            if(pcrs.contains(parts[0].trim()))
            	pcrMp.put(parts[0].trim(), new PcrManifest(Integer.parseInt(parts[0]),parts[1]));
            */
        }
        
        return pcrManifest;
        
    }
    
    /*
	public EntityManagerFactory getEntityManagerFactory() {
		return entityManagerFactory;
	}
	
	public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
	}*/

    /*
	private List<String> getPcrsList() {
		List<String> pcrs = new ArrayList<String>() ;
		
		for(int i = 0 ; i< 24 ; i++)
			pcrs.add(String.valueOf(i));
		
		return pcrs;
	}
	*/

    
    
    
    
    
    
}