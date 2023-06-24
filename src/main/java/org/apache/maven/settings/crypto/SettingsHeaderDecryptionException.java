package org.apache.maven.settings.crypto;

public class SettingsHeaderDecryptionException extends RuntimeException {

    public SettingsHeaderDecryptionException( String message )
    {
        super( message );
    }

    public SettingsHeaderDecryptionException( Throwable cause )
    {
        super( cause );
    }

    public SettingsHeaderDecryptionException( String message, Throwable cause )
    {
        super( message, cause );
    }
}
