package fi.iki.elonen;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class SimpleWebServerGui extends JFrame {
	

    private static final int DEFAULT_PORT = 8080;

    // Pseudo-constant - see usages
    private static String DEFAULT_HOST = "localhost";
    

	static public enum Option {
		HOME("home", 'h'),
		QUIET("quiet", 'q'),
		PORT("port", 'p'),
		DIR("dir", 'd'),
		LICENCE("licence", null),
		HOST("host", null),
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

	
	static public void main(String[] args) {
        int port = DEFAULT_PORT;

        try {
			InetAddress addr = InetAddress.getLocalHost();
			DEFAULT_HOST = addr.getHostAddress();
		} catch (UnknownHostException e) {
			DEFAULT_HOST = "127.0.0.1";
		}
        
        String host = DEFAULT_HOST;
        List<File> rootDirs = new ArrayList<File>();
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
            } else if (Option.LICENCE.matches(argi)) {
                System.out.println(SimpleWebServer.LICENCE + "\n");
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
        
        SimpleWebServer.addBasicOptions(host, port, rootDirs, quiet, options);
        
        SimpleWebServer.loadWebServerPlugins(quiet, options);
        
        final SimpleWebServer server = new SimpleWebServer(host, port, rootDirs, quiet);

		ServerRunner.executeInstance(server, false);
	}
	
    private static void fatal(String msg) {
    	PrintStream ps = System.err;
    	ps.println("?"+msg);
    	ps.println("Usage: "+SimpleWebServerGui.class.getSimpleName()+" [ options ]");
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
    	ps.println(SimpleWebServer.LICENCE);
    	System.exit(1);
    }
	
	private JTextArea messages = new JTextArea();
	private JScrollPane scrollPane = new JScrollPane(messages, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	private  JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
	
	private boolean follow = true;
	private JCheckBox followTail = new JCheckBox("Follow", follow);
	
	
	private JCheckBox quietOption = new JCheckBox("Quiet");
	
	private JButton clear = new JButton(new AbstractAction("Clear") {
		@Override
		public void actionPerformed(ActionEvent e) {
			messages.setText("");
		}
	});
	
	private SimpleWebServer server;
	
	public SimpleWebServerGui(String title, int port, List<File> rootDirs, boolean quiet, final SimpleWebServer serv) throws HeadlessException {
		super(title);
		
		this.server = serv;
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		quietOption.setSelected(quiet);
		quietOption.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				server.setQuiet(quietOption.isSelected());
			}
		});
		
		followTail.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				follow = followTail.isSelected();
			}
		});
		
		StringBuilder tmp = new StringBuilder("Serving files from:\n");
		for (File dir : rootDirs) {
			tmp.append(dir.getPath()).append("\n");
		}
		tmp.append("===========\n");
		messages.setText(tmp.toString());
		
		OutputStream os = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				char ch = (char) b;
				messages.append(new Character(ch).toString());
				if (ch=='\n' && follow) {
					verticalScrollBar.setValue(verticalScrollBar.getMaximum());
				}
			}
		};
		
		PrintStream ps = new PrintStream(os);
		
		System.setErr(ps);
		System.setOut(ps);
		
		server.addPropertyChangeListener(SimpleWebServer.PROPERTY_RUNSTATE, new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				StringBuilder sb = new StringBuilder("Web Server: ");
				sb.append(server.getListeningAddressPort());
				if ( ! server.isAlive()) {
					sb.append(" INACTIVE");
				}
				setTitle(sb.toString());
			}
		});
		
		Box box = Box.createHorizontalBox();
		box.add(clear);
		box.add(followTail);
		box.add(quietOption);
		
		Container cp = getContentPane();
		cp.add(scrollPane, BorderLayout.CENTER);
		cp.add(box, BorderLayout.SOUTH);

		pack();
		setSize(640, 480);
		
		setVisible(true);
	}

}
