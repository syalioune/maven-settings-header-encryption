package org.apache.maven.settings.crypto;

/**
 * Custom exception for decryption errors.
 */
public class SettingsHeaderDecryptionException extends RuntimeException {

    /**
     * {@inheritDoc}
     */
    public SettingsHeaderDecryptionException( String message )
    {
        super( message );
    }

    /**
     * {@inheritDoc}
     */
    public SettingsHeaderDecryptionException( Throwable cause )
    {
        super( cause );
    }

    /**
     * {@inheritDoc}
     */
    public SettingsHeaderDecryptionException( String message, Throwable cause )
    {
        super( message, cause );
    }
}
