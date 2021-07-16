/*
 *
 *  TCPClient from Kurose and Ross
 *  * Compile: java TCPClient.java
 *  * Run: java TCPClient
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.*;
import java.util.concurrent.locks.*;

public class Client extends Thread{
	// main variables
	private static Socket clientSocket;
	private static BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
	private static BufferedReader read;
	private static BufferedReader readp2p;
	private static PrintWriter writep2p;
	private static PrintWriter outToServer;
	private static int serverPort;
	private static InetAddress ip;

	private static int p2pPort;
	private static InetAddress p2pip;

	// thread variables
	private List<String> exit_response = new ArrayList<String>(Arrays.asList("You have successfully logged out.", "You have been logged out due to a timeout."));
	private String response;
	private String log_blocked = "Your account was blocked due to multiple login failures. Please try again later.";
	private String message;
	private String p2pname;

	private boolean p2pInitiated = false;

	@Override
	public void run(){
		String success = "Welcome to CSESMS!";
		String blocked = "Your account has been blocked. Please try again later";
		String timeout = "You have been logged out due to a timeout";
		boolean ready = false;

		while(true){
			try{
				startClient();
				while(response.equals(success) == false && response.equals(blocked) == false && response.equals(log_blocked) == false){
					System.out.println(response);
					startClient();
				}

				if(response.equals(blocked) || response.equals(log_blocked) || response.equals(timeout)){
					System.out.println(response);
					clientSocket.close();
						// close terminal
					System.exit(0);
				}
				// if successfully logged in, we can start sending commands
				if(response.equals(success)){
					Thread readMessages = new printMessages(read);
					readMessages.start();
					ready = true;
					break;
				}
			}catch (Exception e){
				//e.printStackTrace();
				System.out.println(log_blocked);
				//clientSocket.close();
				// close terminal
				System.exit(0);
			}
		}

		// Welcome message for user
		System.out.println(success);
		// we send commands here
		String command;
		Thread readMessages = new printMessages(read);
		readMessages.start();
		// create a thread to accept connections on the port and IP as given by the main function
		try{
			Thread acceptPrivateConnections = new acceptPrivate(new ServerSocket(p2pPort, 0, p2pip));
			acceptPrivateConnections.start();
		} catch (Exception e){
			e.printStackTrace();
		}
		String[] split;
		while(ready){
			try{
				command = inFromUser.readLine();
				outToServer.println(command);
				outToServer.flush();
			} catch (Exception e){
				e.printStackTrace();
			}
		}
	}
	class acceptPrivate extends Thread {
		final ServerSocket welcome;

		public acceptPrivate(ServerSocket welcome){
			this.welcome = welcome;
		}

		@Override
		public void run(){
			while(true){
				try{
					// this client is now listening on port: p2pPort, IP: p2pip.
					Socket p2pclient = welcome.accept();
					// in from client
					readp2p = new BufferedReader(new InputStreamReader(p2pclient.getInputStream()));
					// out to client
					writep2p = new PrintWriter(new OutputStreamWriter(p2pclient.getOutputStream()));
					System.out.println(p2pclient);
					// create a thread for each new p2p client who wants to connect to this user
					Thread t = new P2PClientHandler(p2pclient, readp2p, writep2p);
					t.start();
				} catch (Exception e){
					e.printStackTrace();
				}
			}
		}
	}

	class P2PClientHandler extends Thread{
		private final Socket p2pclient;
		private final BufferedReader readp2p;
		private final PrintWriter writep2p;
		private String message;

		public P2PClientHandler(Socket p2pclient, BufferedReader readp2p, PrintWriter writep2p){
			this.p2pclient = p2pclient;
			this.readp2p = readp2p;
			this.writep2p = writep2p;
		}

		@Override
		public void run(){
			while(true){
				try{
					// read and write the private messages on this separate thread
					String message = readp2p.readLine();
					if(message.equals(null)) break;
					System.out.println(message);
					writep2p.println(message);
					writep2p.flush();
				} catch (SocketException e){
					System.out.println("Server shut down.");
					break;
				} catch (NullPointerException e){
					// when socket is closed, the read will read null pointer into message
					// we can put anything into the .equals argument and it will throw a
					// NullPointerException. We check for this and terminate the loop,
					// which in turn will terminate the thread.
					System.out.println("Private socket terminated");
					break;
				} catch (Exception e){
					e.printStackTrace();
				}
			}
		}
	}
	class printMessages extends Thread {
		final BufferedReader dis;
		String messageReceived;
		public printMessages(BufferedReader dis){
			this.dis = dis;
		}
		String[] broken;
		@Override
		public void run(){
			//recieve messages continuously
			while(true){
				try{
					messageReceived = dis.readLine();
					broken = messageReceived.split(" ", 4);
					// if it is a new P2P connection coming, then dont print message, just handle it
					// This statement only runs for the first connection. It will start the acceptPrivate thread for this user
					System.out.println(messageReceived);
					if(exit_response.contains(messageReceived)){
						// close connection and program
						clientSocket.close();
						System.exit(0);
					}
				} catch (SocketException e){
					//System.out.println("Server shut down. All connections have been closed.");
					return;
				} catch (Exception e){
					e.printStackTrace();
				}
			}
		}
	}
	public static void main(String[] args) throws Exception {

		// Define socket parameters, address and Port No
		if(args.length != 2){
			System.out.println("Required arguments: Server Name, Port");
			return;
		}
		String serverName = args[0];
		serverPort = Integer.parseInt(args[1]);
		ip = InetAddress.getByName(serverName);

		clientSocket = new Socket(ip, serverPort);

		p2pPort = clientSocket.getLocalPort();
		p2pip = clientSocket.getInetAddress();
		//System.out.println(p2pip + " " + p2pPort);
		read = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		outToServer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
		Thread s = new Client();
		s.start();
	} // end of main
	private void startClient() throws SocketException, IOException{
		try{
			String sentence;
		System.out.print("Username: ");
			sentence = inFromUser.readLine();
			outToServer.println(sentence);

			System.out.print("Password: ");
			sentence = inFromUser.readLine();
			outToServer.println(sentence);
			outToServer.flush();

			response = read.readLine();

		} catch (SocketException e){
			System.out.println(log_blocked);
		} catch (Exception e){
			e.printStackTrace();
		}

	}

	private static void logout() throws Exception{
		try{
			clientSocket.close();
		}catch (Exception e){
			e.printStackTrace();
		}
	}
} // end of class TCPClient
