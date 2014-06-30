/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mountwilson.trustagent.commands;

import com.intel.mountwilson.common.CommandUtil;
import com.intel.mountwilson.common.ErrorCode;
import com.intel.mountwilson.common.ICommand;
import com.intel.mountwilson.common.TAException;
import com.intel.mountwilson.trustagent.data.TADataContext;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.BufferedReader;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author skaja
 */
public class GenerateModulesCmd implements ICommand {

    Logger log = LoggerFactory.getLogger(getClass().getName());
    private TADataContext context;

    public GenerateModulesCmd(TADataContext context) {
        this.context = context;
    }

    @Override
    public void execute() throws TAException {
        try {
            getXmlFromMeasureLog();

        } catch (Exception ex) {
            throw new TAException(ErrorCode.ERROR, "Error while getting Module details.", ex);
        }

    }

    /**
     * calls OAT script prepares XML from measureLog
     *
     * @author skaja
     */
    private void getXmlFromMeasureLog() throws TAException, IOException {
        
        log.debug("About to run the command: " + context.getMeasureLogLaunchScript());
        String measureLogLaunchScript = context.getMeasureLogLaunchScript().getAbsolutePath();
        if (!CommandUtil.containsSingleQuoteShellSpecialCharacters(measureLogLaunchScript)) {
            log.warn("Escaping special characters in measureLogLaunchScript path: {}", measureLogLaunchScript);
            measureLogLaunchScript = CommandUtil.escapeShellArgument(measureLogLaunchScript);
        }
        long startTime = System.currentTimeMillis();
        CommandUtil.runCommand(measureLogLaunchScript);
        long endTime = System.currentTimeMillis();
        log.debug("measureLog.xml is created from txt-stat in Duration MilliSeconds {}", (endTime - startTime));

        String content = FileUtils.readFileToString(context.getMeasureLogXmlFile());
        log.debug("Content of the XML file before getting modules: " + content);
        
        getModulesFromMeasureLogXml(content);

        String measureLogXmlFile = context.getMeasureLogXmlFile().getAbsolutePath();
        if (!CommandUtil.containsSingleQuoteShellSpecialCharacters(measureLogXmlFile)) {
            log.warn("Escaping special characters in measureLogXmlFile path: {}", measureLogXmlFile);
            measureLogXmlFile = CommandUtil.escapeShellArgument(measureLogXmlFile);
        }
        CommandUtil.runCommand(String.format("rm -fr %s", measureLogXmlFile));

    }

    /**
     * Obtains <modules> tag under <txt> and add the string to TADataContext
     *
     * @author skaja
     */
    private void getModulesFromMeasureLogXml(String xmlInput) throws TAException {
        try {

            // Since the output from the script will have lot of details and we are interested in just the module section, we will
            // strip out the remaining data,
            Pattern PATTERN = Pattern.compile("(<modules>.*</modules>)");
            Matcher m = PATTERN.matcher(xmlInput);
            while (m.find()) {
                xmlInput = m.group(1);
            }
            // removes any white space characters from the xml string
            String moduleInfo = xmlInput.replaceAll(">\\s*<", "><");
            
            log.debug("Module information : " + moduleInfo);
            
            // If we have XML data, we we will have issues mapping the response to the ClientRequestType using JaxB unmarshaller. So,
            // we will encode the string and send it.
            moduleInfo = Base64.encodeBase64String(moduleInfo.getBytes());
            context.setModules(moduleInfo);
            

        } catch (Exception e) {
            throw new TAException(ErrorCode.BAD_REQUEST, "Cannot find modules in the input xml");
        }

    }
}
