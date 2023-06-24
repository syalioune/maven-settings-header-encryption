package org.apache.maven.settings.crypto;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import java.io.File;

public class EnhancedSettingsHeaderDecrypterTest {

    /**
     * Custom extension bean.
     */
    private EnhancedSettingsHeaderDecrypter enhancedSettingsHeaderDecrypter;

    /**
     * Maven security dispatcher.
     */
    private SecDispatcher secDispatcher;

    @BeforeEach
    public void setup() {
        secDispatcher = Mockito.mock(SecDispatcher.class);
        enhancedSettingsHeaderDecrypter = new EnhancedSettingsHeaderDecrypter(secDispatcher);
    }

    @Test
    public void onEventOtherThanMavenExecutionRequestTest() {
        // Arrange
        // Nothing to be done

        // Act
        enhancedSettingsHeaderDecrypter.onEvent(new Object());

        // Assert
        Mockito.verifyNoInteractions(secDispatcher);
    }


    @Test
    public void settingsWithNoHeadersTest() throws SettingsBuildingException {
        // Arrange
        MavenExecutionRequest executionRequest = buildMavenExecutionRequest("settings-with-no-headers");

        // Act
        enhancedSettingsHeaderDecrypter.onEvent(executionRequest);

        // Assert
        Mockito.verifyNoInteractions(secDispatcher);
    }

    @Test
    public void settingsWithHttpClientHeadersTest() throws SettingsBuildingException, SecDispatcherException {
        // Arrange
        MavenExecutionRequest executionRequest = buildMavenExecutionRequest("settings-with-httpClient-headers");
        Mockito.when(secDispatcher.decrypt("all-header-value")).thenReturn("all-unencrypted-value");

        // Act
        enhancedSettingsHeaderDecrypter.onEvent(executionRequest);

        // Assert
        Mockito.verify(secDispatcher).decrypt("all-header-value");
        Mockito.verifyNoMoreInteractions(secDispatcher);
        Assertions.assertEquals("all-unencrypted-value", getHttpClientHeaderValue(executionRequest.getServers().get(0), "all-header-name"));
    }

    @Test
    public void settingsWithWagonHeadersTest() throws SettingsBuildingException, SecDispatcherException {
        // Arrange
        MavenExecutionRequest executionRequest = buildMavenExecutionRequest("settings-with-wagon-headers");
        Mockito.when(secDispatcher.decrypt("my-encrypted-value")).thenReturn("my-unencrypted-value");

        // Act
        enhancedSettingsHeaderDecrypter.onEvent(executionRequest);

        // Assert
        Mockito.verify(secDispatcher).decrypt("my-encrypted-value");
        Mockito.verifyNoMoreInteractions(secDispatcher);
        Assertions.assertEquals("my-unencrypted-value", getWagonHeaderValue(executionRequest.getServers().get(0), "my-header"));
    }

    @Test
    public void settingsWithMixedHeadersTest() throws SettingsBuildingException, SecDispatcherException {
        // Arrange
        MavenExecutionRequest executionRequest = buildMavenExecutionRequest("settings-with-mixed-headers");
        Mockito.when(secDispatcher.decrypt("my-encrypted-value")).thenReturn("my-unencrypted-value");
        Mockito.when(secDispatcher.decrypt("all-header-value")).thenReturn("all-unencrypted-value");

        // Act
        enhancedSettingsHeaderDecrypter.onEvent(executionRequest);

        // Assert
        Mockito.verify(secDispatcher).decrypt("my-encrypted-value");
        Mockito.verify(secDispatcher).decrypt("all-header-value");
        Mockito.verifyNoMoreInteractions(secDispatcher);
        Assertions.assertEquals("all-unencrypted-value", getHttpClientHeaderValue(executionRequest.getServers().get(0), "all-header-name"));
        Assertions.assertEquals("my-unencrypted-value", getWagonHeaderValue(executionRequest.getServers().get(1), "my-header"));
    }

    @Test
    public void settingsWithThrownExceptionDuringDecryption() throws SettingsBuildingException, SecDispatcherException {
        // Arrange
        MavenExecutionRequest executionRequest = buildMavenExecutionRequest("settings-with-wagon-headers");
        Exception expectedThrownException = new RuntimeException("test-exception");
        Mockito.when(secDispatcher.decrypt("my-encrypted-value")).thenThrow(expectedThrownException);

        // Act
        SettingsHeaderDecryptionException thrownException = Assertions.assertThrows(SettingsHeaderDecryptionException.class, () -> {
            enhancedSettingsHeaderDecrypter.onEvent(executionRequest);
        });

        // Assert
        Mockito.verify(secDispatcher).decrypt("my-encrypted-value");
        Mockito.verifyNoMoreInteractions(secDispatcher);
        Assertions.assertEquals(expectedThrownException, thrownException.getCause());
        Assertions.assertEquals("Unable to decrypt header my-header in generic wagon configuration", thrownException.getMessage());
    }

    /**
     * Read test settings.xml file in test resource by name
     *
     * @param name
     *          The settings filename without xml extension
     *
     * @return A file object corresponding to the settings file
     */
    private File getSettings(String name) {
        return new File("src/test/resources/settings/" + name + ".xml").getAbsoluteFile();
    }

    /**
     * Build a maven execution request event from a settings.xml file name.
     *
     * @param settingFilenameWithoutExtension
     *          The settings filename without xml extension
     *
     * @return maven execution request event
     *
     * @throws SettingsBuildingException
     *          In case of exception during settings build
     */
    private MavenExecutionRequest buildMavenExecutionRequest(String settingFilenameWithoutExtension) throws SettingsBuildingException {
        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        SettingsBuilder builder = new DefaultSettingsBuilderFactory().newInstance();
        DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setSystemProperties(System.getProperties());
        request.setUserSettingsFile(getSettings(settingFilenameWithoutExtension));
        SettingsBuildingResult result = builder.build(request);
        Settings settings = result.getEffectiveSettings();
        executionRequest.setServers(settings.getServers());
        return executionRequest;
    }

    /**
     * Extract a header value from generic wagon configuration.
     *
     * @param server
     *          Settings server object
     *
     * @param headerName
     *          Name of the header to search for
     *
     * @return The header value or null if it was not found
     */
    private String getWagonHeaderValue(Server server, String headerName) {
        String result = null;
        Xpp3Dom config = (Xpp3Dom) server.getConfiguration();
        Xpp3Dom httpHeaders = config.getChild("httpHeaders");
        if (httpHeaders != null) {
            Xpp3Dom[] properties = httpHeaders.getChildren("property");
            for (Xpp3Dom property : properties) {
                if (headerName.equals(property.getChild("name").getValue())) {
                    result = property.getChild("value").getValue();
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Extract a header value from legacy wagon http client configuration.
     *
     * @param server
     *          Settings server object
     *
     * @param headerName
     *          Name of the header to search for
     *
     * @return The header value or null if it was not found
     */
    private String getHttpClientHeaderValue(Server server, String headerName) {
        String result = "";
        Xpp3Dom config = (Xpp3Dom) server.getConfiguration();
        Xpp3Dom httpConfiguration = config.getChild("httpConfiguration");
        if (httpConfiguration != null) {
            Xpp3Dom all = httpConfiguration.getChild("all");
            if(all != null) {
                Xpp3Dom legacyHeaders = all.getChild("headers");
                if (legacyHeaders != null) {
                    Xpp3Dom[] properties = legacyHeaders.getChildren("property");
                    for (Xpp3Dom property : properties) {
                        if (headerName.equals(property.getChild("name").getValue())) {
                            result = property.getChild("value").getValue();
                            break;
                        }
                    }
                }
            }

        }
        return result;
    }

}
