package br.com.santander.rundeck.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepFailureReason;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.StepPlugin;

@Plugin(name = RestSemaphoreDependencyPlugin.PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = "REST Semaphore Dependency Plugin",description = "Enables one project to reference a REST semaphore service as dependency.")
public class RestSemaphoreDependencyPlugin implements StepPlugin {
    public static final String PROVIDER_NAME = "rest-semaphore-dependency-plugin";

    @PluginProperty(title = "Rest URL",
                    description = "REST URL e.g: http[s]://<server>:<port>/<service>?<parameters>")
    String restURL;
    
    @PluginProperty(title = "Response to match for success",
            description = "Response to match to return the successed state")
    String responseForSuccess;
    
    @PluginProperty(title = "Rundeck message on success",
            description = "Message for the successed state")
    String rundeckMsgForSuccess;
    
    @PluginProperty(title = "Rundeck message on error",
            description = "Message for the error state")
    String rundeckMsgForError;
    
    @PluginProperty(title = "Regex Rest URL",
            description = "Regex to apply to REST URL before execution")
    String regexRestURL;
    
    //flow control properties:
    @PluginProperty(title = "Halt",
                    description = "Halt if the condition is not met. If not halted, the workflow execution will continue.")
    boolean halt;
    @PluginProperty(title = "Fail",
                    description = "Halt with fail if the condition is not met, otherwise success.")
    boolean fail;
    
    public void executeStep(PluginStepContext context, Map<String, Object> configuration) throws StepException {
        if (null == restURL) {
            throw new StepException(
                    "Configuration invalid: restURL is required",
                    StepFailureReason.ConfigurationFailure
            );
        }
        if (null == responseForSuccess) {
            throw new StepException(
                    "Configuration invalid: responseForSuccess is required",
                    StepFailureReason.ConfigurationFailure
            );
        }
        // The REST call
        boolean result = checkSemaphoreState( context );

        finishConditional(context, result);
    }

    private void finishConditional(PluginStepContext context, boolean result)
            throws StepException
    {
    	String message;
        if(!result) {
        	message = rundeckMsgForError;
            context.getLogger().log(0, message);
            haltConditionally(context);
        }else{
        	message = rundeckMsgForSuccess;
            context.getLogger().log(2, message);
        }
    }

    private void haltConditionally(PluginStepContext context) {
        if (halt) {
            if (null == context.getFlowControl()) {
                context.getLogger().log(
                        0,
                        "[" +
                        PROVIDER_NAME +
                        "] HALT requested, but no FlowControl available in this context"
                );
                return;
            }
            if (null != rundeckMsgForError) {
                context.getFlowControl().Halt(rundeckMsgForError);
            } else {
                context.getFlowControl().Halt(!fail);
            }
        } else if (context.getFlowControl() != null) {
            context.getFlowControl().Continue();
        }
    }

    public boolean checkSemaphoreState(PluginStepContext context) throws StepException {
    	boolean checkedOK = false; // default in case of service unavailability is false too
    	URL url;
    	OutputStreamWriter out = null;
		try {
			url = new URL( getURLFromAppliedRegex() );
			
			HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
	    	httpCon.setRequestMethod("GET");
	    	
	    	if (httpCon.getResponseCode() != 200) {
	    		throw new StepException("Failed : HTTP error code :" + httpCon.getResponseCode(), StepFailureReason.ConfigurationFailure);
	    	}
	    	BufferedReader br = new BufferedReader(new InputStreamReader((httpCon.getInputStream())));
	    	String result = br.readLine();
	    	
	    	if( result != null && result.equals( responseForSuccess ))  {
	    		checkedOK = true;
	    	}
		} catch (MalformedURLException e) {
			throw new StepException("Malformed URL:" + restURL, StepFailureReason.ConfigurationFailure);
		} catch (IOException ioe) {
			throw new StepException("IOException on URL:" + restURL, StepFailureReason.ConfigurationFailure);
		} finally {
			if( out != null )
				try {
					out.close();
				} catch (IOException e) {} 
		}
		return checkedOK;
    }
    
    private String getURLFromAppliedRegex() {
        if ( StringUtils.isEmpty(regexRestURL)) return restURL;
    	
    	// Sample regexRestURL = (.*)_.*
    	int count = regexRestURL.length() - regexRestURL.replace("(", "").length();
        String replaceValue="";
        for(int replaces = 1; replaces <= count; replaces++)
                replaceValue = replaceValue.concat( "$" + replaces );
        return restURL.replaceAll(regexRestURL, replaceValue);
    }
}
