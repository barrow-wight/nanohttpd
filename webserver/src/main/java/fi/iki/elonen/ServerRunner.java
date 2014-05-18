package fi.iki.elonen;

import java.io.IOException;

public class ServerRunner {
    public static void run(Class<?> serverClass) {
        try {
            executeInstance((NanoHTTPD) serverClass.newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void executeInstance(NanoHTTPD server) {
    	executeInstance(server, true);
    }

    public static IOException executeInstance(NanoHTTPD server, boolean waitForNewline) {
        try {
            server.start();
        } catch (IOException ioe) {
        	if (waitForNewline) {
              System.err.println("Couldn't start server:\n" + ioe);
              System.exit(-1);
        	}
        	return ioe;
        }

        if (waitForNewline) {
            System.out.println("Server started, Hit Enter to stop.\n");

            try {
                System.in.read();
            } catch (Throwable ignored) {
            }

            server.stop();
            System.out.println("Server stopped.\n");
        }
        return null;
    }
}
