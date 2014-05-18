package fi.iki.elonen;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.StringTokenizer;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class SimpleWebServer extends NanoHTTPD {
	
	static public enum Option {
		HOME("home", 'h'),
		QUIET("quiet", 'q'),
		PORT("port", 'p'),
		DIR("dir", 'd'),
		LICENCE("licence", null),
		HOST("host", null),
		GUI("gui", null),
		;
		
		public final String longName;
		public final String shortName;
		
		Option(String longName, Character charName) {
			this.longName = longName;
			this.shortName = charName==null ? null : charName.toString();
		}
		
		public boolean matches(String arg) {
			if (arg.equals("-"+longName)) {
				return true;
			}
			return shortName==null ? false : arg.equals("-"+shortName);
		}
	}

	
    private static final int DEFAULT_PORT = 8080;
    
    private static String DEFAULT_HOST = "localhost";
    
	/**
     * Common mime type for dynamic content: binary
     */
    public static final String MIME_DEFAULT_BINARY = "application/octet-stream";
    /**
     * Default Index file names.
     */
    public static final List<String> INDEX_FILE_NAMES = new ArrayList<String>() {{
        add("index.html");
        add("index.htm");
    }};
    /**
     * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
     */
    private static final Map<String, String> MIME_TYPES = new HashMap<String, String>() {{
        put("css", "text/css");
        put("htm", "text/html");
        put("html", "text/html");
        put("xml", "text/xml");
        put("java", "text/x-java-source, text/java");
        put("md", "text/plain");
        put("txt", "text/plain");
        put("asc", "text/plain");
        put("gif", "image/gif");
        put("jpg", "image/jpeg");
        put("jpeg", "image/jpeg");
        put("png", "image/png");
        put("mp3", "audio/mpeg");
        put("m3u", "audio/mpeg-url");
        put("mp4", "video/mp4");
        put("ogv", "video/ogg");
        put("flv", "video/x-flv");
        put("mov", "video/quicktime");
        put("swf", "application/x-shockwave-flash");
        put("js", "application/javascript");
        put("pdf", "application/pdf");
        put("doc", "application/msword");
        put("ogg", "application/x-ogg");
        put("zip", "application/octet-stream");
        put("exe", "application/octet-stream");
        put("class", "application/octet-stream");
    }};
    /**
     * The distribution licence
     */
    private static final String LICENCE =
        "Copyright (c) 2012-2013 by Paul S. Hawke, 2001,2005-2013 by Jarno Elonen, 2010 by Konstantinos Togias\n"
            + "\n"
            + "Redistribution and use in source and binary forms, with or without\n"
            + "modification, are permitted provided that the following conditions\n"
            + "are met:\n"
            + "\n"
            + "Redistributions of source code must retain the above copyright notice,\n"
            + "this list of conditions and the following disclaimer. Redistributions in\n"
            + "binary form must reproduce the above copyright notice, this list of\n"
            + "conditions and the following disclaimer in the documentation and/or other\n"
            + "materials provided with the distribution. The name of the author may not\n"
            + "be used to endorse or promote products derived from this software without\n"
            + "specific prior written permission. \n"
            + " \n"
            + "THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR\n"
            + "IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES\n"
            + "OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.\n"
            + "IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,\n"
            + "INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT\n"
            + "NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,\n"
            + "DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY\n"
            + "THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT\n"
            + "(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE\n"
            + "OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";
    private static Map<String, WebServerPlugin> mimeTypeHandlers = new HashMap<String, WebServerPlugin>();
    private final List<File> rootDirs;
    private boolean quiet;

    public SimpleWebServer(String host, int port, File wwwroot, boolean quiet) {
        super(host, port);
        this.quiet = quiet;
        this.rootDirs = new ArrayList<File>();
        this.rootDirs.add(wwwroot);

        this.init();
    }

    public SimpleWebServer(String host, int port, List<File> wwwroots, boolean quiet) {
        super(host, port);
        this.quiet = quiet;
        this.rootDirs = new ArrayList<File>(wwwroots);

        this.init();
    }

	/**
	 * Used to initialize and customize the server.
	 */
    public void init() {
    }

    public void setQuiet(boolean q) {
    	this.quiet = q;
    }
    
    private static void fatal(String msg) {
    	PrintStream ps = System.err;
    	ps.println("?"+msg);
    	ps.println("Usage: "+SimpleWebServer.class.getSimpleName()+" [ options ]");
    	ps.println("Starts a web server");
    	ps.println("Options:");
    	ps.println("-gui             display a user interface for monitoring");
    	ps.println("-d DIR           add DIR as a root dir from which to serve files");
    	ps.println("                 default is only the current directory");
    	ps.println("-h HOSTNAME      listen as hostname (default "+DEFAULT_HOST+")");
    	ps.println("-p PORT          listen on port (default "+DEFAULT_PORT+")");
    	ps.println("-q               start in 'quiet' mode (default is ! gui)");
    	ps.println("-v               start in 'verbose' mode (default is ! gui)");
    	ps.println("-X:option-value  add an option value which is passed to plugins");
    	ps.println();
    	ps.println("---------------------------------------------------------------");
    	ps.println(LICENCE);
    	System.exit(1);
    }
    /**
     * Starts as a standalone file server and waits for Enter.
     */
    public static void main(String[] args) {
        // Defaults
        int port = DEFAULT_PORT;

        try {
			InetAddress addr = InetAddress.getLocalHost();
			DEFAULT_HOST = addr.getHostAddress();
		} catch (UnknownHostException e) {
			DEFAULT_HOST = "127.0.0.1";
		}
        
        String host = DEFAULT_HOST;
        List<File> rootDirs = new ArrayList<File>();
        boolean gui = false;
        boolean quiet = false;
        Map<String, String> options = new HashMap<String, String>();

        // Parse command-line, with short and long versions of the options.
        for (int i = 0; i < args.length; ++i) {
        	String argi = args[i];
            if (Option.HOST.matches(argi)) {
            	if (++i >= args.length || args[i].startsWith("-")) {
            		fatal("missing value for "+argi);
            	}
                host = args[i];
            } else if (Option.PORT.matches(argi)) {
            	if (++i >= args.length || args[i].startsWith("-")) {
            		fatal("missing value for "+argi);
            	}
                port = Integer.parseInt(args[i]);
            } else if (Option.QUIET.matches(argi)) {
                quiet = true;
            } else if (Option.DIR.matches(argi)) {
            	if (++i >= args.length || args[i].startsWith("-")) {
            		fatal("missing value for "+argi);
            	}
                rootDirs.add(new File(args[i]).getAbsoluteFile());
            } else if (Option.GUI.matches(argi)) {
                gui = true;
            } else if (Option.LICENCE.matches(argi)) {
                System.out.println(LICENCE + "\n");
            } else if (argi.startsWith("-X:")) {
                int dot = argi.indexOf('=');
                if (dot > 0) {
                    String name = argi.substring(0, dot);
                    String value = argi.substring(dot + 1, argi.length());
                    options.put(name, value);
                } else {
                	fatal("missing value for "+argi);
                }
            } else {
            	fatal("unsupported arg: "+argi);
            }
        }

        if (rootDirs.isEmpty()) {
            rootDirs.add(new File(".").getAbsoluteFile());
        }

        addBasicOptions(host, port, rootDirs, quiet, options);
        
        loadWebServerPlugins(quiet, options);
        
        final SimpleWebServer server = new SimpleWebServer(host, port, rootDirs, quiet);

        if (gui) {
        	startGui(port, rootDirs, quiet, server);
        }

		ServerRunner.executeInstance(server, ! gui);
    }
    
    public static void loadWebServerPlugins(boolean quiet, Map<String, String> options) {
		ServiceLoader<WebServerPluginInfo> serviceLoader = ServiceLoader.load(WebServerPluginInfo.class);
        for (WebServerPluginInfo info : serviceLoader) {
            String[] mimeTypes = info.getMimeTypes();
            for (String mime : mimeTypes) {
                String[] indexFiles = info.getIndexFilesForMimeType(mime);
                if (!quiet) {
                    System.out.print("# Found plugin for Mime type: \"" + mime + "\"");
                    if (indexFiles != null) {
                        System.out.print(" (serving index files: ");
                        for (String indexFile : indexFiles) {
                            System.out.print(indexFile + " ");
                        }
                    }
                    System.out.println(").");
                }
                registerPluginForMimeType(indexFiles, mime, info.getWebServerPlugin(mime), options);
            }
        }
	}
    
	private static void addBasicOptions(String host, int port, List<File> rootDirs, boolean quiet, Map<String, String> options) {
		options.put(Option.HOST.longName, host);
        options.put(Option.PORT.longName, Integer.toString(port));
        options.put(Option.QUIET.longName, String.valueOf(quiet));
        StringBuilder sb = new StringBuilder();
        for (File dir : rootDirs) {
            if (sb.length() > 0) {
                sb.append(":");
            }
            try {
                sb.append(dir.getCanonicalPath());
            } catch (IOException ignored) {}
        }
        options.put(Option.HOME.longName, sb.toString());
	}
	
	private static void startGui(int port, List<File> rootDirs, boolean quiet, final SimpleWebServer server) {
		StringBuilder tmp = new StringBuilder("Serving files from:\n");
		for (File dir : rootDirs) {
			tmp.append(dir.getPath()).append("\n");
		}
		tmp.append("===========\n");
		
		final JFrame frame = new JFrame("Web Server");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		final JTextArea messages = new JTextArea(tmp.toString());
		JScrollPane scrollPane = new JScrollPane(messages, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		final JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();

		JButton clear = new JButton(new AbstractAction("Clear") {
			@Override
			public void actionPerformed(ActionEvent e) {
				messages.setText("");
			}
		});

		final JCheckBox quietOption = new JCheckBox("Quiet", quiet);
		quietOption.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				server.setQuiet(quietOption.isSelected());
			}
		});

		final boolean[] follow = new boolean[] { true };
		final JCheckBox followTail = new JCheckBox("Follow", follow[0]);

		followTail.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				follow[0] = followTail.isSelected();
			}
		});
		
		
		OutputStream os = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				char ch = (char) b;
				messages.append(new Character(ch).toString());
				if (ch=='\n' && follow[0]) {
					verticalScrollBar.setValue(verticalScrollBar.getMaximum());
				}
			}
		};
		
		PrintStream ps = new PrintStream(os);
		
		System.setErr(ps);
		System.setOut(ps);
		
		Box box = Box.createHorizontalBox();
		box.add(clear);
		box.add(followTail);
		box.add(quietOption);
		
		Container cp = frame.getContentPane();
		cp.add(scrollPane, BorderLayout.CENTER);
		cp.add(box, BorderLayout.SOUTH);

		frame.pack();
		frame.setSize(640, 480);
		
		frame.setVisible(true);
		
		server.addPropertyChangeListener(SimpleWebServer.PROPERTY_RUNSTATE, new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				StringBuilder sb = new StringBuilder("Web Server: ");
				sb.append(server.getListeningAddressPort());
				if ( ! server.isAlive()) {
					sb.append(" INACTIVE");
				}
				frame.setTitle(sb.toString());
			}
		});
	}

    protected static void registerPluginForMimeType(String[] indexFiles, String mimeType, WebServerPlugin plugin, Map<String, String> commandLineOptions) {
        if (mimeType == null || plugin == null) {
            return;
        }

        if (indexFiles != null) {
            for (String filename : indexFiles) {
                int dot = filename.lastIndexOf('.');
                if (dot >= 0) {
                    String extension = filename.substring(dot + 1).toLowerCase();
                    MIME_TYPES.put(extension, mimeType);
                }
            }
            INDEX_FILE_NAMES.addAll(Arrays.asList(indexFiles));
        }
        mimeTypeHandlers.put(mimeType, plugin);
        plugin.initialize(commandLineOptions);
    }

    public File getRootDir() {
        return rootDirs.get(0);
    }

    public List<File> getRootDirs() {
        return Collections.unmodifiableList(rootDirs);
    }

    public void addWwwRootDir(File wwwroot) {
        rootDirs.add(wwwroot);
    }

    /**
     * URL-encodes everything between "/"-characters. Encodes spaces as '%20' instead of '+'.
     */
    private String encodeUri(String uri) {
        String newUri = "";
        StringTokenizer st = new StringTokenizer(uri, "/ ", true);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            if (tok.equals("/"))
                newUri += "/";
            else if (tok.equals(" "))
                newUri += "%20";
            else {
                try {
                    newUri += URLEncoder.encode(tok, "UTF-8");
                } catch (UnsupportedEncodingException ignored) {
                }
            }
        }
        return newUri;
    }

    public Response serve(IHTTPSession session) {
        Map<String, String> header = session.getHeaders();
        Map<String, String> parms = session.getParms();
        String uri = session.getUri();

        if (!quiet) {
            System.out.println(session.getMethod() + " '" + uri + "' ");

            Iterator<String> e = header.keySet().iterator();
            while (e.hasNext()) {
                String value = e.next();
                System.out.println("  HDR: '" + value + "' = '" + header.get(value) + "'");
            }
            e = parms.keySet().iterator();
            while (e.hasNext()) {
                String value = e.next();
                System.out.println("  PRM: '" + value + "' = '" + parms.get(value) + "'");
            }
        }

        for (File homeDir : getRootDirs()) {
            // Make sure we won't die of an exception later
            if (!homeDir.isDirectory()) {
                return getInternalErrorResponse("given path is not a directory (" + homeDir + ").");
            }
        }
        return respond(Collections.unmodifiableMap(header), session, uri);
    }

    private Response respond(Map<String, String> headers, IHTTPSession session, String uri) {
        // Remove URL arguments
        uri = uri.trim().replace(File.separatorChar, '/');
        if (uri.indexOf('?') >= 0) {
            uri = uri.substring(0, uri.indexOf('?'));
        }

        // Prohibit getting out of current directory
        if (uri.startsWith("src/main") || uri.endsWith("src/main") || uri.contains("../")) {
            return getForbiddenResponse("Won't serve ../ for security reasons.");
        }

        boolean canServeUri = false;
        File homeDir = null;
        List<File> roots = getRootDirs();
        for (int i = 0; !canServeUri && i < roots.size(); i++) {
            homeDir = roots.get(i);
            canServeUri = canServeUri(uri, homeDir);
        }
        if (!canServeUri) {
            return getNotFoundResponse();
        }

        // Browsers get confused without '/' after the directory, send a redirect.
        File f = new File(homeDir, uri);
        if (f.isDirectory() && !uri.endsWith("/")) {
            uri += "/";
            Response res = createResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, "<html><body>Redirected: <a href=\"" +
                uri + "\">" + uri + "</a></body></html>");
            res.addHeader("Location", uri);
            return res;
        }

        if (f.isDirectory()) {
            // First look for index files (index.html, index.htm, etc) and if none found, list the directory if readable.
            String indexFile = findIndexFileInDirectory(f);
            if (indexFile == null) {
                if (f.canRead()) {
                    // No index file, list the directory if it is readable
                    return createResponse(Response.Status.OK, NanoHTTPD.MIME_HTML, listDirectory(uri, f));
                } else {
                    return getForbiddenResponse("No directory listing.");
                }
            } else {
                return respond(headers, session, uri + indexFile);
            }
        }

        String mimeTypeForFile = getMimeTypeForFile(uri);
        WebServerPlugin plugin = mimeTypeHandlers.get(mimeTypeForFile);
        Response response = null;
        if (plugin != null) {
            response = plugin.serveFile(uri, headers, session, f, mimeTypeForFile);
            if (response != null && response instanceof InternalRewrite) {
                InternalRewrite rewrite = (InternalRewrite) response;
                return respond(rewrite.getHeaders(), session, rewrite.getUri());
            }
        } else {
            response = serveFile(uri, headers, f, mimeTypeForFile);
        }
        return response != null ? response : getNotFoundResponse();
    }

    protected Response getNotFoundResponse() {
        return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT,
            "Error 404, file not found.");
    }

    protected Response getForbiddenResponse(String s) {
        return createResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "FORBIDDEN: "
            + s);
    }

    protected Response getInternalErrorResponse(String s) {
        return createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
            "INTERNAL ERRROR: " + s);
    }

    private boolean canServeUri(String uri, File homeDir) {
        boolean canServeUri;
        File f = new File(homeDir, uri);
        canServeUri = f.exists();
        if (!canServeUri) {
            String mimeTypeForFile = getMimeTypeForFile(uri);
            WebServerPlugin plugin = mimeTypeHandlers.get(mimeTypeForFile);
            if (plugin != null) {
                canServeUri = plugin.canServeUri(uri, homeDir);
            }
        }
        return canServeUri;
    }

    /**
     * Serves file from homeDir and its' subdirectories (only). Uses only URI, ignores all headers and HTTP parameters.
     */
    Response serveFile(String uri, Map<String, String> header, File file, String mime) {
        Response res;
        try {
            // Calculate etag
            String etag = Integer.toHexString((file.getAbsolutePath() + file.lastModified() + "" + file.length()).hashCode());

            // Support (simple) skipping:
            long startFrom = 0;
            long endAt = -1;
            String range = header.get("range");
            if (range != null) {
                if (range.startsWith("bytes=")) {
                    range = range.substring("bytes=".length());
                    int minus = range.indexOf('-');
                    try {
                        if (minus > 0) {
                            startFrom = Long.parseLong(range.substring(0, minus));
                            endAt = Long.parseLong(range.substring(minus + 1));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Change return code and add Content-Range header when skipping is requested
            long fileLen = file.length();
            if (range != null && startFrom >= 0) {
                if (startFrom >= fileLen) {
                    res = createResponse(Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
                    res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
                    res.addHeader("ETag", etag);
                } else {
                    if (endAt < 0) {
                        endAt = fileLen - 1;
                    }
                    long newLen = endAt - startFrom + 1;
                    if (newLen < 0) {
                        newLen = 0;
                    }

                    final long dataLen = newLen;
                    FileInputStream fis = new FileInputStream(file) {
                        @Override
                        public int available() throws IOException {
                            return (int) dataLen;
                        }
                    };
                    fis.skip(startFrom);

                    res = createResponse(Response.Status.PARTIAL_CONTENT, mime, fis);
                    res.addHeader("Content-Length", "" + dataLen);
                    res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
                    res.addHeader("ETag", etag);
                }
            } else {
                if (etag.equals(header.get("if-none-match")))
                    res = createResponse(Response.Status.NOT_MODIFIED, mime, "");
                else {
                    res = createResponse(Response.Status.OK, mime, new FileInputStream(file));
                    res.addHeader("Content-Length", "" + fileLen);
                    res.addHeader("ETag", etag);
                }
            }
        } catch (IOException ioe) {
            res = getForbiddenResponse("Reading file failed.");
        }

        return res;
    }

    // Get MIME type from file name extension, if possible
    private String getMimeTypeForFile(String uri) {
        int dot = uri.lastIndexOf('.');
        String mime = null;
        if (dot >= 0) {
            mime = MIME_TYPES.get(uri.substring(dot + 1).toLowerCase());
        }
        return mime == null ? MIME_DEFAULT_BINARY : mime;
    }

    // Announce that the file server accepts partial content requests
    private Response createResponse(Response.Status status, String mimeType, InputStream message) {
        Response res = new Response(status, mimeType, message);
        res.addHeader("Accept-Ranges", "bytes");
        return res;
    }

    // Announce that the file server accepts partial content requests
    private Response createResponse(Response.Status status, String mimeType, String message) {
        Response res = new Response(status, mimeType, message);
        res.addHeader("Accept-Ranges", "bytes");
        return res;
    }

    private String findIndexFileInDirectory(File directory) {
        for (String fileName : INDEX_FILE_NAMES) {
            File indexFile = new File(directory, fileName);
            if (indexFile.exists()) {
                return fileName;
            }
        }
        return null;
    }

    protected String listDirectory(String uri, File f) {
        String heading = "Directory " + uri;
        StringBuilder msg = new StringBuilder("<html><head><title>" + heading + "</title><style><!--\n" +
            "span.dirname { font-weight: bold; }\n" +
            "span.filesize { font-size: 75%; }\n" +
            "// -->\n" +
            "</style>" +
            "</head><body><h1>" + heading + "</h1>");

        String up = null;
        if (uri.length() > 1) {
            String u = uri.substring(0, uri.length() - 1);
            int slash = u.lastIndexOf('/');
            if (slash >= 0 && slash < u.length()) {
                up = uri.substring(0, slash + 1);
            }
        }

        List<String> files = Arrays.asList(f.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isFile();
            }
        }));
        Collections.sort(files);
        List<String> directories = Arrays.asList(f.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isDirectory();
            }
        }));
        Collections.sort(directories);
        if (up != null || directories.size() + files.size() > 0) {
            msg.append("<ul>");
            if (up != null || directories.size() > 0) {
                msg.append("<section class=\"directories\">");
                if (up != null) {
                    msg.append("<li><a rel=\"directory\" href=\"").append(up).append("\"><span class=\"dirname\">..</span></a></b></li>");
                }
                for (String directory : directories) {
                    String dir = directory + "/";
                    msg.append("<li><a rel=\"directory\" href=\"").append(encodeUri(uri + dir)).append("\"><span class=\"dirname\">").append(dir).append("</span></a></b></li>");
                }
                msg.append("</section>");
            }
            if (files.size() > 0) {
                msg.append("<section class=\"files\">");
                for (String file : files) {
                    msg.append("<li><a href=\"").append(encodeUri(uri + file)).append("\"><span class=\"filename\">").append(file).append("</span></a>");
                    File curFile = new File(f, file);
                    long len = curFile.length();
                    msg.append("&nbsp;<span class=\"filesize\">(");
                    if (len < 1024) {
                        msg.append(len).append(" bytes");
                    } else if (len < 1024 * 1024) {
                        msg.append(len / 1024).append(".").append(len % 1024 / 10 % 100).append(" KB");
                    } else {
                        msg.append(len / (1024 * 1024)).append(".").append(len % (1024 * 1024) / 10 % 100).append(" MB");
                    }
                    msg.append(")</span></li>");
                }
                msg.append("</section>");
            }
            msg.append("</ul>");
        }
        msg.append("</body></html>");
        return msg.toString();
    }
}
