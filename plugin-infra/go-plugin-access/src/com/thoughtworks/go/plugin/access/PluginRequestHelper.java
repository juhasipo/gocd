package com.thoughtworks.go.plugin.access;

import com.thoughtworks.go.plugin.api.request.DefaultGoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.infra.PluginManager;
import org.apache.log4j.Logger;

import java.util.List;

import static java.lang.String.format;

public class PluginRequestHelper {

    private static final Logger LOG = Logger.getLogger(PluginRequestHelper.class);

    protected PluginManager pluginManager;
    private List<String> goSupportedVersions;
    private String extensionName;

    public PluginRequestHelper(PluginManager pluginManager, List<String> goSupportedVersions, String extensionName) {
        this.pluginManager = pluginManager;
        this.goSupportedVersions = goSupportedVersions;
        this.extensionName = extensionName;
    }

    public <T> T submitRequest(String pluginId, String requestName, PluginInteractionCallback<T> pluginInteractionCallback) {
        waitForPluginToBeLoaded(pluginId, 10);

        if (!pluginManager.isPluginOfType(extensionName, pluginId)) {
            String msg = format("Did not find '%s' plugin with id '%s'. Looks like plugin is missing", extensionName, pluginId);
            LOG.error(msg);
            throw new RuntimeException(msg);
        }
        try {
            String resolvedExtensionVersion = pluginManager.resolveExtensionVersion(pluginId, goSupportedVersions);
            DefaultGoPluginApiRequest apiRequest = new DefaultGoPluginApiRequest(extensionName, resolvedExtensionVersion, requestName);
            apiRequest.setRequestBody(pluginInteractionCallback.requestBody(resolvedExtensionVersion));
            apiRequest.setRequestParams(pluginInteractionCallback.requestParams(resolvedExtensionVersion));
            GoPluginApiResponse response = pluginManager.submitTo(pluginId, apiRequest);
            if(response == null){
                throw new RuntimeException("The plugin sent a null response");
            }
            if (DefaultGoApiResponse.SUCCESS_RESPONSE_CODE == response.responseCode()) {
                return pluginInteractionCallback.onSuccess(response.responseBody(), resolvedExtensionVersion);
            }
            throw new RuntimeException(format("The plugin sent a response that could not be understood by Go. Plugin returned with code '%s' and the following response: '%s'", response.responseCode(), response.responseBody()));
        } catch (Exception e) {
            throw new RuntimeException(format("Interaction with plugin with id '%s' implementing '%s' extension failed while requesting for '%s'. Reason: [%s]", pluginId, extensionName, requestName, e.getMessage()), e);
        }
    }

    private void waitForPluginToBeLoaded(String pluginId, final int maxRetries) {
        int retries = 0;
        do {
            try {
                LOG.debug("Checking that required plugin is available...");
                if (!pluginManager.isPluginOfType(extensionName, pluginId)) {
                    final int secondsToWait = 5;

                    retries++;

                    LOG.debug("Could not run just yet because plugins have not been fully loaded");
                    LOG.debug(format("Retrying in %d seconds. Retry count: %d", secondsToWait, retries));

                    Thread.sleep(secondsToWait * 1000);
                } else {
                    break;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while(retries < maxRetries);
    }
}
