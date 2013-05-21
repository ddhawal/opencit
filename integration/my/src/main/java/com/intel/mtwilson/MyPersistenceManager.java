/*
 * Copyright (C) 2013 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson;

//import com.intel.mountwilson.as.common.ASConfig;
//import com.intel.mtwilson.audit.helper.AuditConfig;
import com.intel.mtwilson.jpa.PersistenceManager;
//import com.intel.mtwilson.ms.common.MSConfig;
import java.util.Properties;
import javax.persistence.EntityManagerFactory;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example:
 * 
        Properties jdbc = new Properties();
        jdbc.setProperty("mtwilson.db.host", "10.1.71.88");
        jdbc.setProperty("mtwilson.db.schema", "mw_as");
        jdbc.setProperty("mtwilson.db.user", "root");
        jdbc.setProperty("mtwilson.db.password", "password");
        jdbc.setProperty("mtwilson.db.port", "3306");
        jdbc.setProperty("mtwilson.as.dek", "hPKk/2uvMFRAkpJNJgoBwA=="); // optional;  if you don't set this ,the value you see here is the default
        CustomPersistenceManager pm = new CustomPersistenceManager(jdbc);

 *
 * @author jbuhacoff
 */
public class MyPersistenceManager extends PersistenceManager {
    private transient static Logger log = LoggerFactory.getLogger(MyPersistenceManager.class);
    private Properties jdbcProperties;
    public MyPersistenceManager(Properties jdbcProperties) {
        this.jdbcProperties = jdbcProperties;
    }
    @Override
    public void configure() {
        log.debug("MyPersistenceManager: Database Host: {}", jdbcProperties.getProperty("mtwilson.db.host"));
        MyConfiguration c = new MyConfiguration(jdbcProperties);
        addPersistenceUnit("ASDataPU", getASDataJpaProperties(c));
        addPersistenceUnit("MSDataPU", getMSDataJpaProperties(c));
        addPersistenceUnit("AuditDataPU", getAuditDataJpaProperties(c));
    }
    // XXX TODO get dek from MyConfiguration instead of from the jdbcProperties
    public byte[] getDek() {
        return Base64.decodeBase64(jdbcProperties.getProperty("mtwilson.as.dek", "hPKk/2uvMFRAkpJNJgoBwA==")); // arbitrary default dek, since it's a development server it's good to use same as what is configured there, but it doesn't matter as it only affects records we are writing, and hopefully after each test is complete there is zero net effect on the database
    }
    public EntityManagerFactory getASData() {
        return getEntityManagerFactory("ASDataPU");
    }
    public EntityManagerFactory getMSData() {
        return getEntityManagerFactory("MSDataPU");
    }
    public EntityManagerFactory getAuditData() {
        return getEntityManagerFactory("AuditDataPU");
    }
    
    public static Properties getJpaProperties(MyConfiguration config) {
        Properties prop = new Properties();
        prop.put("javax.persistence.jdbc.driver", config.getDatabaseDriver());
        prop.put("javax.persistence.jdbc.scheme", config.getDatabaseProtocol()); // XXX if everything is working without this now, remove it
        String url = String.format("jdbc:%s://%s:%s/%s?autoReconnect=true",
                config.getDatabaseProtocol(), config.getDatabaseHost(), 
                config.getDatabasePort(), config.getDatabaseSchema());
        prop.put("javax.persistence.jdbc.url", url);
        prop.put("javax.persistence.jdbc.user", config.getDatabaseUsername());
        prop.put("javax.persistence.jdbc.password", config.getDatabasePassword());
        System.err.println("getJpaProps Default url == " + prop.getProperty("javax.persistence.jdbc.url"));
        return prop;
    }
    
    public static Properties getASDataJpaProperties(MyConfiguration config) {
        Properties prop = new Properties();
        Configuration myConfig = config.getConfiguration();
        prop.put("javax.persistence.jdbc.driver", myConfig.getString("mountwilson.as.db.driver",config.getDatabaseDriver()));
        if( prop.get("javax.persistence.jdbc.driver").equals("com.mysql.jdbc.Driver") ) {
            prop.put("javax.persistence.jdbc.scheme", "mysql"); // NOTE: this is NOT a standard javax.persistence property, we are setting it for our own use
        }
        else if( prop.get("javax.persistence.jdbc.driver").equals("org.postgresql.Driver") ) {
            prop.put("javax.persistence.jdbc.scheme", "postgresql"); // NOTE: this is NOT a standard javax.persistence property, we are setting it for our own use
        }
        else {
            prop.put("javax.persistence.jdbc.scheme", "unknown-scheme");
        }
        prop.put("javax.persistence.jdbc.url" , 
                myConfig.getString("mountwilson.as.db.url",
                myConfig.getString("mtwilson.db.url",
                String.format("jdbc:%s://%s:%s/%s?autoReconnect=true",
                    prop.get("javax.persistence.jdbc.scheme"),
                    myConfig.getString("mountwilson.as.db.host", myConfig.getString("mtwilson.db.host","127.0.0.1")),
                    myConfig.getString("mountwilson.as.db.port", myConfig.getString("mtwilson.db.port","3306")),
                    myConfig.getString("mountwilson.as.db.schema", myConfig.getString("mtwilson.db.schema","mw_as"))))));
        prop.put("javax.persistence.jdbc.user",
                myConfig.getString("mountwilson.as.db.user",
                myConfig.getString("mtwilson.db.user",
                "root")));
        prop.put("javax.persistence.jdbc.password", 
                myConfig.getString("mountwilson.as.db.password", 
                myConfig.getString("mtwilson.db.password", 
                "password")));
        System.err.println("getJpaProps ASdata url == " + prop.getProperty("javax.persistence.jdbc.url"));
        return prop;
    }    
    
    public static Properties getMSDataJpaProperties(MyConfiguration config) {
        Properties prop = new Properties();
        Configuration myConfig = config.getConfiguration();
        prop.put("javax.persistence.jdbc.driver", 
                myConfig.getString("mountwilson.ms.db.driver", 
               config.getDatabaseDriver()));
        if( prop.get("javax.persistence.jdbc.driver").equals("com.mysql.jdbc.Driver") ) {
            prop.put("javax.persistence.jdbc.scheme", "mysql"); // NOTE: this is NOT a standard javax.persistence property, we are setting it for our own use
        }
        else if( prop.get("javax.persistence.jdbc.driver").equals("org.postgresql.Driver") ) {
            prop.put("javax.persistence.jdbc.scheme", "postgresql"); // NOTE: this is NOT a standard javax.persistence property, we are setting it for our own use
        }
        else {
            prop.put("javax.persistence.jdbc.scheme", "unknown-scheme");
        }        
        prop.put("javax.persistence.jdbc.url" , 
                myConfig.getString("mountwilson.ms.db.url",
                myConfig.getString("mtwilson.db.url",
                String.format("jdbc:%s://%s:%s/%s?autoReconnect=true",
                    prop.getProperty("javax.persistence.jdbc.scheme"),
                    myConfig.getString("mountwilson.ms.db.host", myConfig.getString("mtwilson.db.host","127.0.0.1")),
                    myConfig.getString("mountwilson.ms.db.port", myConfig.getString("mtwilson.db.port","3306")),
                    myConfig.getString("mountwilson.ms.db.schema", myConfig.getString("mtwilson.db.schema","mw_as"))))));
        prop.put("javax.persistence.jdbc.user",
                myConfig.getString("mountwilson.ms.db.user",
                myConfig.getString("mtwilson.db.user",
                "root")));
        prop.put("javax.persistence.jdbc.password", 
                myConfig.getString("mountwilson.ms.db.password", 
                myConfig.getString("mtwilson.db.password", 
                "password")));
        System.err.println("getJpaProps MSData url == " + prop.getProperty("javax.persistence.jdbc.url"));
        return prop;
        
    }
    
    
    public static Properties getAuditDataJpaProperties(MyConfiguration config) {
        Properties prop = new Properties();
        Configuration myConfig = config.getConfiguration();
        prop.put("javax.persistence.jdbc.driver", 
                myConfig.getString("mountwilson.audit.db.driver", 
                config.getDatabaseDriver()));
        if( prop.get("javax.persistence.jdbc.driver").equals("com.mysql.jdbc.Driver") ) {
            prop.put("javax.persistence.jdbc.scheme", "mysql"); // NOTE: this is NOT a standard javax.persistence property, we are setting it for our own use
        }
        else if( prop.get("javax.persistence.jdbc.driver").equals("org.postgresql.Driver") ) {
            prop.put("javax.persistence.jdbc.scheme", "postgresql"); // NOTE: this is NOT a standard javax.persistence property, we are setting it for our own use
        }
        else {
            prop.put("javax.persistence.jdbc.scheme", "unknown-scheme");
        }        
        prop.put("javax.persistence.jdbc.url" , 
                myConfig.getString("mountwilson.audit.db.url",
                myConfig.getString("mtwilson.db.url",
                String.format("jdbc:%s://%s:%s/%s?autoReconnect=true",
                    prop.getProperty("javax.persistence.jdbc.scheme"),
                    myConfig.getString("mountwilson.audit.db.host", myConfig.getString("mtwilson.db.host","127.0.0.1")),
                    myConfig.getString("mountwilson.audit.db.port", myConfig.getString("mtwilson.db.port","3306")),
                    myConfig.getString("mountwilson.audit.db.schema", myConfig.getString("mtwilson.db.schema","mw_as"))))));
        prop.put("javax.persistence.jdbc.user",
                myConfig.getString("mountwilson.audit.db.user",
                myConfig.getString("mtwilson.db.user",
                "root")));
        prop.put("javax.persistence.jdbc.password", 
                myConfig.getString("mountwilson.audit.db.password", 
                myConfig.getString("mtwilson.db.password", 
                "password")));
        System.err.println("getJpaProps audit url == " + prop.getProperty("javax.persistence.jdbc.url"));
        return prop;
        
    }
 
    public static Properties getMCDataJpaProperties(MyConfiguration config) {
        Properties prop = new Properties();
        Configuration myConfig = config.getConfiguration();
        prop.put("javax.persistence.jdbc.driver", 
                myConfig.getString("mountwilson.mc.db.driver", 
                config.getDatabaseDriver()));
        if( prop.get("javax.persistence.jdbc.driver").equals("com.mysql.jdbc.Driver") ) {
            prop.put("javax.persistence.jdbc.scheme", "mysql"); // NOTE: this is NOT a standard javax.persistence property, we are setting it for our own use
        }
        else if( prop.get("javax.persistence.jdbc.driver").equals("org.postgresql.Driver") ) {
            prop.put("javax.persistence.jdbc.scheme", "postgresql"); // NOTE: this is NOT a standard javax.persistence property, we are setting it for our own use
        }
        else {
            prop.put("javax.persistence.jdbc.scheme", "unknown-scheme");
        }        
        prop.put("javax.persistence.jdbc.url" , 
                myConfig.getString("mountwilson.mc.db.url",
                myConfig.getString("mtwilson.db.url",
                String.format("jdbc:%s://%s:%s/%s?autoReconnect=true",
                    prop.getProperty("javax.persistence.jdbc.scheme"),
                    myConfig.getString("mountwilson.mc.db.host", myConfig.getString("mtwilson.db.host","127.0.0.1")),
                    myConfig.getString("mountwilson.mc.db.port", myConfig.getString("mtwilson.db.port","3306")),
                    myConfig.getString("mountwilson.mc.db.schema", myConfig.getString("mtwilson.db.schema","mw_as"))))));
        prop.put("javax.persistence.jdbc.user",
                myConfig.getString("mountwilson.mc.db.user",
                myConfig.getString("mtwilson.db.user",
                "root")));
        prop.put("javax.persistence.jdbc.password", 
                myConfig.getString("mountwilson.mc.db.password", 
                myConfig.getString("mtwilson.db.password", 
                "password")));
        System.err.println("getJpaProps MCData url == " + prop.getProperty("javax.persistence.jdbc.url"));
        return prop;
        
    }    
    
}
