/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mtwilson.tag.client.jaxrs;

import com.intel.dcsg.cpg.io.UUID;
import com.intel.mtwilson.client.jaxrs.common.MtWilsonClient;
import java.net.URL;
import java.util.Properties;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 *
 * @author ssbangal
 */
public class MtWilsonImportTagCertificate extends MtWilsonClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Selections.class);

    public MtWilsonImportTagCertificate(URL url) throws Exception{
        super(url);
    }

    public MtWilsonImportTagCertificate(Properties properties) throws Exception {
        super(properties);
    }    

        
    /**
     * This function imports the specified certificate into Mt.Wilson. If the host is 
     * already registered with Mt.Wilson, the certificate would be automatically mapped to the host.
     * @param UUID of the certificate that needs to be imported.
     * @since Mt.Wilson 2.0
     * @mtwRequiresPermissions tag_certificates:import
     * @mtwContentTypeReturned JSON/XML/YAML
     * @mtwMethodType POST
     * @mtwSampleRestCall
     * <pre>
     * https://server.com:8181/mtwilson/v2/rpc/mtwilson-import-tag-certificate
     * Input: {"certificate_id":"a6544ff4-6dc7-4c74-82be-578592e7e3ba"}
     * </pre>
     * @mtwSampleApiCall
     * <pre>
     *  MtWilsonImportTagCertificate client = new MtWilsonImportTagCertificate(My.configuration().getClientProperties());
     *  client.mtwilsonImportTagCertificate("a6544ff4-6dc7-4c74-82be-578592e7e3ba");
     * </pre>
     */
    public void mtwilsonImportTagCertificate(UUID certificateId) {
        log.debug("target: {}", getTarget().getUri().toString());
        Response obj = getTarget().path("rpc/mtwilson-import-tag-certificate").request().accept(MediaType.APPLICATION_JSON).post(Entity.json(certificateId));
    }
        
}
