package com.sibilantsolutions.iscloseddemo;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Demo race condition in Socket.isClosed() after having called Socket.close().
 *
 * - Connect a socket to a host.
 * - Start a thread and call the socket's InputStream.read().
 * - From a different thread, call socket.close().
 * - This will cause read() to throw an exception.
 * - In the exception handling, check the value of socket.isClosed().  It should be true.  However,
 *   in SSL sockets, it is often false.  Sleeping for a short time and checking again will
 *   then correctly say isClosed() is true.  It SHOULD have been true all along.
 *
 * Note that there are two scenarios which can cause read() to throw an exception:
 * 1) Calling Socket.close() from a different thread, which is what we demo here.
 * 2) Experiencing an external network error like a TCP connection reset.  We do not address
 *    this scenario in this demo.
 */
public class IsClosedDemo
{

    static private boolean isBugEncountered = false;

    static public void main( String[] args )
    {
        int i = 0;
        String host = args[i++];
        int port = Integer.parseInt( args[i++] );
        boolean isSSL = Boolean.parseBoolean( args[i++] );
        long sleepMs = Long.parseLong( args[i++] );

        int loopCount;

        for( loopCount = 0; ! isBugEncountered; loopCount++ )
        {
            System.out.println( "Testing (loop=" + (loopCount+1) + ") against host=" + host + ", port=" + port + ", ssl=" + isSSL +
                    ", sleepMs=" + sleepMs + '.' );

            Socket socket = connect( host, port, isSSL );

            Thread readThread = readInThread( socket, sleepMs );

            try
            {
                    //Let the read thread get started.
                Thread.sleep( 5 * 1000 );
            }
            catch ( InterruptedException e )
            {
                // TODO Auto-generated catch block
                throw new UnsupportedOperationException( "OGTE TODO!", e );
            }

            testInThread( socket );

            try
            {
                    //Wait for the read thread to die before deciding whether to loop again.
                readThread.join();
            }
            catch ( InterruptedException e )
            {
                // TODO Auto-generated catch block
                throw new UnsupportedOperationException( "MY TODO!", e );
            }
        }

        System.out.println( "Finished after " + loopCount + " attempt(s)." );
    }

    private static Socket connect( String host, int port, boolean isSSL )
    {
        SocketFactory socketFactory;

        if ( isSSL )
            socketFactory = SSLSocketFactory.getDefault();
        else
            socketFactory = SocketFactory.getDefault();

        Socket socket;

        try
        {
            socket = socketFactory.createSocket( host, port );
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            throw new UnsupportedOperationException( "OGTE TODO!", e );
        }

        System.out.println( "Connected socket=" + socket );

        if ( isSSL )
        {
            SSLSocket sslSocket = (SSLSocket)socket;

            sslSocket.addHandshakeCompletedListener( new HandshakeCompletedListener() {

                public void handshakeCompleted( HandshakeCompletedEvent event )
                {
                    System.out.println( "SSL handshake completed: " + event.getSocket() + "." );
                }
            } );

            //SSL handshake will occur automatically when we attempt to read from the socket later.
        }

        return socket;
    }

    private static Thread readInThread( final Socket socket, final long sleepMs )
    {
        Runnable r = new Runnable() {

            public void run()
            {
                InputStream ins;

                try
                {
                    ins = socket.getInputStream();
                }
                catch ( IOException e )
                {
                    // TODO Auto-generated catch block
                    throw new UnsupportedOperationException( "OGTE TODO!", e );
                }

                byte[] buf = new byte[1024];

                System.out.println( "Calling read..." );

                try
                {
                    int numRead = ins.read( buf );

                        //Should not get here in this demo; we call Socket.close() locally to
                        //cause read to unblock and throw the exception which is handled below.s
                    System.out.println( "numRead=" + numRead );
                }
                catch ( IOException e )
                {
                        //Check if the socket is closed, which -should- be true after Socket.close()
                        //has been called.
                    boolean originalIsClosed = socket.isClosed();

                    if ( sleepMs > 0 )
                    {
                        try
                        {
                            Thread.sleep( sleepMs );
                        }
                        catch ( InterruptedException e1 )
                        {
                            // TODO Auto-generated catch block
                            throw new UnsupportedOperationException( "OGTE TODO!", e1 );
                        }
                    }

                        //Now that we have slept for a moment, check again if the socket is closed.
                        //This -should not- change from the value prior to sleeping.
                    boolean nowIsClosed = socket.isClosed();

                    if ( originalIsClosed != nowIsClosed )
                    {
                        System.out.println(
                                "\n" +
                                "***********************\n" +
                                "*** BUG BUG BUG BUG BUG\n" +
                                "***\n" +
                                "*** Value of isClosed changed from=" + originalIsClosed +
                                " to=" + nowIsClosed + "!!!!!\n" +
                                "***\n" +
                                "*** BUG BUG BUG BUG BUG\n" +
                                "***********************" );

                        isBugEncountered = true;
                    }
                    else
                    {
                        System.out.println( "Bug not encountered.  Value of isClosed stayed=" +
                                originalIsClosed + '.' );
                    }
                }
            }
        };

        Thread thread = new Thread( r );
        thread.start();

        return thread;
    }

    private static void testInThread( final Socket socket )
    {
        Runnable r = new Runnable() {

            public void run()
            {
                System.out.println( "Calling close." );

                try
                {
                    socket.close();
                }
                catch ( IOException e )
                {
                    // TODO Auto-generated catch block
                    throw new UnsupportedOperationException( "OGTE TODO!", e );
                }
            }
        };

        Thread thread = new Thread( r );
        thread.start();
    }

}
