package org.apache.maven.settings.crypto;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Extension spy allowing to eventually decrypt http headers defined in the maven settings file.
 * Those HTTP headers should be encrypted using the methode described here https://maven.apache.org/guides/mini/guide-encryption.html
 */
@Named
@Singleton
public class EnhancedSettingsHeaderDecrypter extends AbstractEventSpy {

    /**
     * Maven security dispatcher.
     */
    private final SecDispatcher securityDispatcher;

    /**
     * Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(EnhancedSettingsHeaderDecrypter.class);


    @Inject
    public EnhancedSettingsHeaderDecrypter(@Named("maven") SecDispatcher securityDispatcher) {
        this.securityDispatcher = securityDispatcher;
    }

    @Override
    public void onEvent(final Object event) {
        if (event instanceof MavenExecutionRequest) {
            LOGGER.debug("Starting processing maven execution request");
            DefaultMavenExecutionRequest request = (DefaultMavenExecutionRequest)  event;
            List<Server> servers = new ArrayList<>();
            for (Server server : request.getServers()) {
                server = server.clone();
                LOGGER.debug("Handling server with id "+server.getId());
                servers.add(server);
                if (server.getConfiguration() != null) {
                    // https://maven.apache.org/guides/mini/guide-http-settings.html#Taking_Control_of_Your_HTTP_Headers
                    Xpp3Dom config = (Xpp3Dom) server.getConfiguration();
                    manageStandardGeneralWagonConfiguration(config);
                    manageLegacyHttpClientWagonConfiguration(config);
                    server.setConfiguration(config);
                }
            }
            request.setServers(servers);
            LOGGER.debug("Finished processing maven execution request");
        }
    }

    /**
     * Handle httpHeaders in standard wagon configuration
     *
     * @param config
     *          The configuration of a specific server definition
     */
    private void manageStandardGeneralWagonConfiguration(Xpp3Dom config) {
        Xpp3Dom httpHeaders = config.getChild("httpHeaders");
        if (httpHeaders != null) {
            Xpp3Dom[] properties = httpHeaders.getChildren("property");
            for (Xpp3Dom property : properties) {
                String headerName = property.getChild("name").getValue();
                LOGGER.debug("Trying to decrypt value for HTTP header "+headerName);
                String headerValue = property.getChild("value").getValue();
                try {
                    String newValue = decrypt(headerValue);
                    property.getChild("value").setValue(newValue);
                } catch (Exception e) {
                    LOGGER.error("Error when trying to decrypt value for HTTP header "+headerName);
                    throw new SettingsHeaderDecryptionException("Unable to decrypt header "+headerName+" in generic wagon configuration", e);
                }
                LOGGER.debug("HTTP header "+headerName+" value decrypted successfully");
            }
        }
    }

    /**
     * Handle httpConfiguration in standard wagon configuration
     *
     * @param config
     *          The configuration of a specific server definition
     */
    private void manageLegacyHttpClientWagonConfiguration(Xpp3Dom config) {
        // https://maven.apache.org/guides/mini/guide-http-settings.html#configuring-get-head-put-or-all-of-the-above
        // We consider only the "all" settings as maven 3.9.0+ does
        Xpp3Dom httpConfiguration = config.getChild("httpConfiguration");
        if (httpConfiguration != null) {
            Xpp3Dom all = httpConfiguration.getChild("all");
            if(all != null) {
                Xpp3Dom legacyHeaders = all.getChild("headers");
                if (legacyHeaders != null) {
                    Xpp3Dom[] properties = legacyHeaders.getChildren("property");
                    for (Xpp3Dom property : properties) {
                        String headerName = property.getChild("name").getValue();
                        LOGGER.debug("Trying to decrypt value for HTTP header "+headerName);
                        String headerValue = property.getChild("value").getValue();
                        try {
                            String newValue = decrypt(headerValue);
                            property.getChild("value").setValue(newValue);
                        } catch (Exception e) {
                            LOGGER.error("Error when trying to decrypt value for HTTP header "+headerName);
                            throw new SettingsHeaderDecryptionException("Unable to decrypt header "+headerName+" in legacy wagon httpClient (all) configuration", e);
                        }
                        LOGGER.debug("HTTP header "+headerName+" value decrypted successfully");
                    }
                }
            }
        }
    }

    /**
     * Decrypt a string.
     *
     * @param str
     *          The string to decrypt
     *
     * @return The decrypted string if it was encrypted or the same string otherwise.
     *
     * @throws SecDispatcherException
     *          In case of any issue during decryption
     */
    private String decrypt(String str) throws SecDispatcherException {
        return (str == null) ? null : securityDispatcher.decrypt(str);
    }
}
