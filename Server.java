/*
 *
 * TCPServer from Kurose and Ross
 * Compile: javac TCPServer.java
 * Run: java TCPServer
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.time.*;

public class Server extends Thread{
			// Global variables
			// server intialisation data
			private static int block_duration;
			private static int timeout;
			private static int serverPort;
			private static ServerSocket welcomeSocket;

			// temporary client data (server stores this temporarily to pass onto new threads)
			private String sAddr;
			private Socket client;


		  // serverdata
			// map name to Socket
			private static HashMap<String, Socket> users = new HashMap<String, Socket>();
			// user (key) has blocked user (value)
			private static HashMap<String, List<String>> active_blocks = new HashMap<String, List<String>>();
			private static HashMap<String, List<Integer>> log_in_times = new HashMap<String, List<Integer>>();
			private static HashMap<String, List<Integer>> log_out_times = new HashMap<String, List<Integer>>();
			private HashMap<String, List<String>> pending_messages = new HashMap<String, List<String>>();
			private HashMap<String, List<Socket>> p2pSock = new HashMap<String, List<Socket>>();
			private static List<Socket> clients = new ArrayList<Socket>();
			private static List<String> blockedClients = new ArrayList<String>();
			private static List<String> usernames = new ArrayList<String>();
			private static List<String> passwords = new ArrayList<String>();
			private static String current_blocked_ip;
			private static String current_blocked_username;
			private static int[] blockedUsernames;
			private static int serverRunTime;

			// server commands and broadcast data
			private static List<String> commands = new ArrayList<String>(Arrays.asList("message", "broadcast", "whoelse", "whoelsesince", "block", "unblock", "logout", "startprivate", "private", "stopprivate"));
			private static List<String> online_users = new ArrayList<String>();

			//userdata
			private String last_read; // last read command from user

			//P2P data
			private static HashMap<String, Integer> writingSockets = new HashMap<String, Integer>();
			private static HashMap<String, Socket> whoInPrivate = new HashMap<String, Socket>();

			//****** RUN FOR SERVER *********//
			@Override
			public void run (){
				// create instance of Timer to count Server time
				Thread systemTime = new Timer();
				systemTime.start();
				while (true){ // keep checking for clients
				    // accept connection from connection queue
						try{
							client = welcomeSocket.accept();
						 	sAddr =  client.getInetAddress().getHostAddress();
						 	BufferedReader dis = new BufferedReader(new InputStreamReader(client.getInputStream()));
						 	PrintWriter dos = new PrintWriter(new OutputStreamWriter(client.getOutputStream()));
						 	// make thread for new client
							//System.out.println("Assigning new thread for this client.");
						 	Thread t = new ClientHandler(client, dis, dos);
						 	//invoke run by starting Thread for this client
						 	t.start();
						}
						catch (Exception e){
							e.printStackTrace();
						}
				}
			}
			//****** MAIN FUNCTION *********//
			public static void main(String[] args)throws Exception {
        /* define socket parameters, Address + PortNo, Address will default to localhost */
				if(args.length != 3){
		    	//System.out.println("Required arguments: Port, Block Duration, Timeout");
		      return;
		    }
				InetAddress ip = InetAddress.getByName("127.0.0.1");
				serverPort = Integer.parseInt(args[0]);
				block_duration = Integer.parseInt(args[1]);
				timeout = Integer.parseInt(args[2]);
				/* change above port number if required */

				/*create server socket that is assigned the serverPort (6789)
		        We will listen on this port for connection request from clients */
				welcomeSocket = new ServerSocket(serverPort, 0, ip);
		    System.out.println("Server is ready.");
				// ****** GET USERNAMES AND PASSWORDS ********
				getCredentials("Credentials.txt");

				// create a single server thread
				Thread s = new Server();
				s.start();
			}
			public static void getCredentials(String path) throws Exception{
				String[] split; // used to split line by space

				File file = new File(path);
				BufferedReader br = new BufferedReader(new FileReader(file));
				String line;
				int i = 0;
				while((line = br.readLine()) != null){
					split = line.split(" ");
					usernames.add(split[0]);
					passwords.add(split[1]);
					i++;
				}
				blockedUsernames = new int[i];
				for(int j = 0; j < i; j++) blockedUsernames[j] = 0;
			}
			class Timer extends Thread{
				final long startTime = System.currentTimeMillis();
				private long millis;
				public Timer(){}

				@Override
				public void run(){
					while(true){
						millis = System.currentTimeMillis();
						serverRunTime = (int)((millis - this.startTime) / 1000);
					}
				}
			}
			// handle everything to do with Client
			class ClientHandler extends Thread {
				// Client data
				final BufferedReader dis;
				final PrintWriter dos;
				final Socket s;
				private Boolean timed_out;
				private String name;
				private Boolean logged_in;
				private String message;
				private int new_block;
				private List<Integer> time_in = new ArrayList<Integer>();			// times at which this user has logged in
				private List<Integer> time_out = new ArrayList<Integer>();		// times at which this user has logged in
				private List<String> whoIsBlocked = new ArrayList<String>();	// who this user has blocked
				private List<String> blacklist = new ArrayList<String>();			// who this user has been blocked by

				// P2P client userdata
				private int p2pPort;
				private String p2pip;
				// list of sockets that this User is connected to
				private List<Socket> privateConnections = new ArrayList<Socket>();
				private Socket welcomeSocket;

				public ClientHandler(Socket s, BufferedReader dis, PrintWriter dos)
				{
					this.s = s;
					this.dis = dis;
					this.dos = dos;
				}

				@Override
				public void run(){
					while (true)
					{
						logged_in = false;
						try{
							logIn(dis, dos);
							break;
						}	catch (Exception e){
							e.printStackTrace();
						}
					}
					if(!logged_in) return;
					try
					{
						//System.out.println(online_users);
						// set the timeout value to input value
						s.setSoTimeout(timeout*1000);
						timed_out = false;
						// if we reach here then someone has logged in.
						// store the log_in time associated with this user in the hashmap on serverRunTime
						// time_in is a building value, continuous log, so it never decreases in size
						time_in.add(serverRunTime);
						log_in_times.put(name, time_in);
						if(!clients.contains(s)){
							// update client list and HashMap
							users.put(name, s);
							clients.add(s);
							//System.out.println("Clients are: " + clients);
						}
						// we need to feed in the blacklist prior to the logged in presence notification
						// so do it now, then notify all other online users of this login (unblocked logged in users)
						for(String x : active_blocks.keySet()){
							for(String y : active_blocks.get(x)){
								if(y.equals(name) && !blacklist.contains(x)) blacklist.add(x);
							}
						}
						message = name + " logged in.";
						// if client timed out, destroy thread
						presenceNotification(message);
						// start CommandHandler (since login successful)
						// CommandHandler also handles timeout value
						Thread commandhandler = new CommandHandler(dis, dos, timeout);
						commandhandler.start();
						// send any pending messages
						//** IMPLEMENT OFFLINE MESSAGE STORING here
						if(!pending_messages.isEmpty()){
							for(String i : pending_messages.keySet()){
								// find the name of current user in pending messages
								if(i.equals(name) && !pending_messages.get(i).isEmpty()){
									// send welcome message
									dos.println("You have pending messages!");
									dos.flush();
									for(String j : pending_messages.get(name)){
										// print the messages on successive lines
										dos.println(j);
										dos.flush();
									}
									// then clear the messages
									pending_messages.get(name).clear();
								}
							}
						}
						// FOR P2P connections
						// every client knows the IP and PORT that they are listening for connections on
						// IP
						p2pip = s.getInetAddress().getHostAddress();
						// PORT (NOT LOCAL PORT, BUT OPERATING PORT CHOSEN BY OS)
						p2pPort = s.getPort();
						writingSockets.put(name, p2pPort);
						// initiate the serverValue, if not set, for private connections
						while(true){
							if(timed_out){
								// STOP THREAD
								Thread.currentThread().interrupt();
								return;
							}
						}
							//this.dis.close();
							//this.dos.close();
					}	catch(IOException e){
							e.printStackTrace();
						}
				}
				// used to store when users log in and logout
				class BlockHandler extends Thread{
					final int[] blockedUsernames;
					final List<String> blockedClients;
					final int block_duration;
					final String current_blocked_ip;
					final String current_blocked_username;

					public BlockHandler(int[] blockedUsernames, List<String> blockedClients, int block_duration, String current_blocked_ip, String current_blocked_username){
						this.blockedUsernames = blockedUsernames;
						this.blockedClients = blockedClients;
						this.block_duration = block_duration;
						this.current_blocked_ip = current_blocked_ip;
						this.current_blocked_username = current_blocked_username;
					}

					@Override
					public void run(){
						// put a block_duration block on the IP address and username
						// after block_duration, remove the IP address from blocked clients, and make blockedUsernames[username] = 0;
						while(new_block == 1){
							try{
								//System.out.println(blockedClients);
								//System.out.println(blockedUsernames);
								Thread.sleep(block_duration * 1000);
								blockedClients.remove(blockedClients.indexOf(current_blocked_ip));
								blockedUsernames[usernames.indexOf(current_blocked_username)] = 0;
								//System.out.println(blockedClients);
								//System.out.println(blockedUsernames);
								new_block = 0;
							} catch (Exception e){
								e.printStackTrace();
							}
						}
					}
				}
				public void presenceNotification(String message){
					PrintWriter dos;
					Socket next;
					// we want to skip if we reach the current user or a user in this ones blocked listen
					for(String k : users.keySet()){
						if(blacklist.contains(k) || k.equals(name)) continue;
						next = users.get(k);
						try{
							dos = new PrintWriter(new OutputStreamWriter(next.getOutputStream()));
							dos.println(message);
							dos.flush();
						} catch (Exception e){
							e.printStackTrace();
						}
					}
				}
				// A constant reader, throws socketexception if nothing read after "timeout" seconds

				class CommandHandler extends Thread{
					final BufferedReader dis;
					final PrintWriter dos;
					final int timeout;
					final String timeout_message = "You have been logged out due to a timeout.";
					final String logout_success = "You have successfully logged out.";
					private String[] broken;

					public CommandHandler(BufferedReader dis, PrintWriter dos, int timeout){
						this.dis = dis;
						this.dos = dos;
						this.timeout = timeout;
					}
					// checks the active_blocks list to see if a certain block exists
					public Boolean isBlocked(String user1, String user2){
						for(String x : active_blocks.keySet()){
							for(String y : active_blocks.get(x)){
								if(x.equals(user1) && y.equals(user2)){
									return true;
								}
							}
						}
						message = "Error. " + broken[1] + " was not blocked.";
						dos.println(message);
						dos.flush();
						return false;
					}

					@Override
					public void run(){
						// command stored in last_read
						while(!timed_out){
							try{
								// UPDATE BLACKLIST
								//System.out.println("Server Run Time: " + serverRunTime);
								blacklist.clear();
								//System.out.println("attempting to read command from " + name);
								last_read = dis.readLine();
								//System.out.println(last_read);
								// update this users blacklist
								//System.out.println("*********Updating Blacklist*******");
								// check active blocks to find if current user has been blocked by anyone
								// if so, add those users to this ones blacklist
								//System.out.println("WRITING SOCKETS");
								//System.out.println(writingSockets);
								//System.out.println("**************");
								//System.out.println("WHO IS IN PRIVATE CHAT");
								//System.out.println(whoInPrivate);
								//System.out.println("**************");
								for(String x : active_blocks.keySet()){
									for(String y : active_blocks.get(x)){
										if(y.equals(name) && !blacklist.contains(x)) blacklist.add(x);
									}
								}
								//System.out.println("List of current blocks is: " + active_blocks);
								// UPDATE P2P OPERATING SOCKETS

								//******* PROCESSING OF COMMANDS ************
								// invalid command
								broken = last_read.split(" ", 3); // max 3 arguments
								if(!commands.contains(last_read) && !commands.contains(broken[0])){
									message = "Error. Invalid Command.";
									dos.println(message);
									dos.flush();
								}else{
									// else must be valid command
									// who else command
									if(last_read.equals("whoelse")){
										Iterator<String> onlineusers = online_users.iterator();
										String next = "";
										while(onlineusers.hasNext()){
											next = onlineusers.next();
											if(next.equals(name)) continue;
											dos.println(next);
											dos.flush();
										}
									}
									// logout command
									if(last_read.equals("logout")){
										dos.println(logout_success);
										dos.flush();
										online_users.remove(name);
										clients.remove(s);
										users.remove(name);
										// send presence notification to rest of usernames
										message = name + " logged out.";
										presenceNotification(message);
										// store the log out time associated with this user in the hashmap on serverRunTime
										// time_in is a building value, continuous log, so it never decreases in size
										time_out.add(serverRunTime);
										log_out_times.put(name, time_out);
										return;
									}
									// message command
									if(broken[0].equals("message")){
										if(broken.length != 3){
											message = "Invalid arguments for message command. Please specify <recipient> <message>.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// if user is blocked, do nothing
										//System.out.println(blacklist + "!!!!!!!!!!!");
										if(blacklist.contains(broken[1])){
											message = "Your message could not be delivered as the recipient has blocked you.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// if name of recipient is name of client, give error
										if(name.equals(broken[1])){
											message = "Error. Cannot message self.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// if user not in usernames, then doesnt exist
										if(!usernames.contains(broken[1])){
											message = "Error. That recipient does not exist.";
											dos.println(message);
											dos.flush();
										}
										// if we get here then we are sending a message
										message = name + ": " + broken[2];
										// if name is online
										if(online_users.contains(broken[1])){
											// create output stream for relevant users
											PrintWriter p2pwriter = new PrintWriter(new OutputStreamWriter(users.get(broken[1]).getOutputStream()));
											p2pwriter.println(message);
											p2pwriter.flush();
										}else{ // user not online, so store message and deliver when online
											// add to messagePlayback list for broken[1] user
											// if there are no pending messages for the user in question, then just ovewrite the buffer
											List<String> append = new ArrayList<String>();
											//System.out.println("User " + broken[1] + " was not online. Messages will be sent when online");
											if(!pending_messages.keySet().contains(broken[1]) || pending_messages.get(broken[1]).isEmpty()){
												append.add(message);
												pending_messages.put(broken[1], append);
											// otherwise we need to append the current message to the already exisiting buffer of pending readMessages
											}else{

												append = pending_messages.get(broken[1]);
												// append the message to the existing buffer
												append.add(message);
												// write it back to the hashmap
												pending_messages.put(broken[1], append);
											}
										}
									}
									if(broken[0].equals("broadcast")){
											if(broken.length < 2) {
												message = "Invalid arguments for broadcast command. Please specify message to broadcast.";
												dos.println(message);
												dos.flush();
												continue;
											}
											//System.out.println("Broken[2] is: " + broken[2]);
											int y = 0; // counter if failure to broadcast due to blacklist (will always be 1)
											for (String i : users.keySet()){
												// if not current user, and user is not blocked
												if(!i.equals(name) && !blacklist.contains(i)){
													//System.out.println(i);
													//System.out.println(name + "'s blacklist: " + blacklist);
													// get socket associated with name
													Socket sock = (Socket) users.get(i);
													PrintWriter p2pwriter = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
													// try printing 3rd element, if it doesnt exist, print first 2
													try{
														p2pwriter.println(name + ": " + broken[1] + " " + broken[2]);
													} catch (ArrayIndexOutOfBoundsException e){
														p2pwriter.println(name + ": " + broken[1]);
													}
													p2pwriter.flush();
												}else y++;
											}
											if(y > 1){ //always 1 due to incrementing when trying to self broadcast, therefore if larger than 1 then at least one
												//other recipient did not receive the broadcast
												message = "Your message could not be delivered to some recipients.";
												dos.println(message);
												dos.flush();
											}
									}
									if(broken[0].equals("block")){
											if(broken.length != 2){
												message = "Invalid arguments for block command. Please enter a user's name.";
												dos.println(message);
												dos.flush();
												continue;
											}
											if(broken[1].equals(name)){
												message = "Error. Cannot block self.";
												dos.println(message);
												dos.flush();
												continue;
											}
											if(!usernames.contains(broken[1])){
												message = "User does not exist.";
												dos.println(message);
												dos.flush();
												continue;
											}
											// if the current set of blocks is not empty and the current user exists in the set
											if(!active_blocks.isEmpty() && active_blocks.keySet().contains(name)){
												if(active_blocks.get(name).contains(broken[1])){
													message = "User is already blocked.";
													dos.println(message);
													dos.flush();
													continue;
												}
											}
											//System.out.println(name + "'s blacklist: " + blacklist);
											// add block to hashmap
											whoIsBlocked.add(broken[1]);
											active_blocks.put(name, whoIsBlocked);
											message = broken[1] + " is blocked.";
											dos.println(message);
											dos.flush();
									}
									if(broken[0].equals("unblock")){
											if(broken.length != 2){
												message = "Invalid arguments for unblock command. Please enter a user's name.";
												dos.println(message);
												dos.flush();
												continue;
											}
											// if block does not exist then
											if(!isBlocked(name, broken[1])) continue;
											if(!usernames.contains(broken[1])){
												message = "User does not exist.";
												dos.println(message);
												dos.flush();
												continue;
											}
											//System.out.println(name + "'s blacklist: " + blacklist);
											whoIsBlocked.remove(broken[1]);
											active_blocks.put(name, whoIsBlocked);
											message = broken[1] + " is unblocked.";
											dos.println(message);
											dos.flush();
									}
									if(broken[0].equals("whoelsesince")){
										if(broken.length != 2){
											message = "Invalid number of arguments for whoelsesince command.";
											dos.println(message);
											dos.flush();
											continue;
										}
										int requestedTime = Integer.parseInt(broken[1]);
										if(requestedTime <= 0){
											message = "Invalid argument. Please enter a time in seconds larger than 0.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// if value larger than serverRunTime then print all users that logged in
										if(requestedTime > serverRunTime){
											for(String i : log_in_times.keySet()){
												if(!i.equals(name)){
													dos.println(i);
													dos.flush();
												}
											}
											continue;
										}

										// if a log_in has occured in this window, then return the users name
										int lower_bound = serverRunTime - requestedTime;
										int higher_bound = serverRunTime;
										//System.out.println(log_in_times);
										//System.out.println(log_out_times);
										String previous ="";
										for(String i : log_in_times.keySet()){
											for(int getLogInTime : log_in_times.get(i)){
												// if the user never logged out, they should show up
												if(!log_out_times.keySet().contains(i)){
													if(!i.equals(name)){
														//System.out.println("never logged out");
														dos.println(i);
														dos.flush();
														break;
													}
												}
												// if the given time logged in is between the lower and higher bounds

												// then that user should show up as a result of this command
												// i.e. this user was logged in during this window
												if(getLogInTime >= lower_bound && getLogInTime <= higher_bound && !i.equals(name)){
													dos.println(i);
													//System.out.println("login found in window");
													dos.flush();
													break;
												} // if there is no log in time then check if there is a log out time
												else{
													for(String y : log_out_times.keySet()){
														for(int getLogOutTime : log_out_times.get(y)){
															// if the user logged out during the window, then they
															// must have been logged in at some point in that window
															if(getLogOutTime >= lower_bound && getLogOutTime <= higher_bound && !y.equals(name) && !y.equals(previous)){
																//System.out.println("none login but logout found in window");
																dos.println(y);
																dos.flush();
																previous = y;
																break;
															}
														}
													}
												}
											}
										}

									}
									if(broken[0].equals("startprivate")){
										if(broken.length != 2){
											message = "Invalid arguments for startprivate command. Please specify <recipient>.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// cant P2P if user doesnt exist
										if(!usernames.contains(broken[1])){
											//user does not exits
											message = "Cannot start P2P connection. The requested user does not exist.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// Cant P2P if user not online
										if(!users.keySet().contains(broken[1])){
											//user not connected/not online
											message = "Cannot start P2P connection. The requested user is not online.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// if connection already exists, it will be in whoInPrivate
										// Hashmap has (clientserver) pair as key and Socket as value
										String check = name + broken[1];
										if(whoInPrivate.keySet().contains(check)){
											message = "P2P connection already exists with " + broken[1];
											dos.println(message);
											dos.flush();
											continue;
										}
										// cant P2P if blocked
										if(blacklist.contains(broken[1])){
											message = "Cannot start P2P connection. The requested user has blocked you.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// cant P2P with self
										if(name.equals(broken[1])){
											message = "Cannot start P2P connection with self.";
											dos.println(message);
											dos.flush();
											continue;
										}

										// if user exists, not blocked and online, then attempt TCP connections
										// Attempt a TCP connection to the IP and Port of the server
										try{
											Socket p2pConnect = new Socket(p2pip, writingSockets.get(broken[1]));
											whoInPrivate.put(name + broken[1], p2pConnect);
											// Socket that communicates with broken[1] is now in p2pConnect
											// We need to use this socket to write out and read from
											//System.out.println(p2pip + " " + p2pPort);
										} catch (Exception e){
											System.out.println("Bad socket parameters!");
										}
									}
									if(broken[0].equals("private")){
										// if length not 3 then error
										if(broken.length != 3){
											message = "Invalid arguments for private command. Please specify <recipient> <message>.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// if username doesnt exist then error
										if(!usernames.contains(broken[1])){
											//user does not exits
											message = "Cannot send message. The requested user does not exist.";
											dos.println(message);
											dos.flush();
											continue;
										}
										if(!users.keySet().contains(broken[1])){
											//user not connected/not online
											message = "Cannot send private message. The requested user is not online.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// send message on private TCP connection (get the right output printwriter)
										// if the connection exists
										if(!writingSockets.keySet().contains(broken[1])){
											message = "Error. This user does not have P2P active yet. P2P is only activated on a successful log in.";
											dos.println(message);
											dos.flush();
											continue;
										}
										//System.out.println(whoInPrivate);
										String check = name + broken[1];
										String check2 = broken[1] + name;
										if(whoInPrivate.keySet().contains(check)){
											//System.out.println(whoInPrivate);
											Socket privateRecipient = whoInPrivate.get(check);
											PrintWriter p2p = new PrintWriter(new OutputStreamWriter(whoInPrivate.get(check).getOutputStream()));
											message = name + "(private): " + broken[2];
											p2p.println(message);
											p2p.flush();
										// if reply is coming back from recipient of private connection request
										}else{
											message = "Error. Private messaging to " + broken[1] + " not enabled.";
											dos.println(message);
											dos.flush();
										}
									}
									if(broken[0].equals("stopprivate")){
										// if length not 3 then error
										if(broken.length != 2){
											message = "Invalid arguments for stopprivate command. Please specify <recipient>.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// if username doesnt exist then error
										if(!usernames.contains(broken[1])){
											//user does not exits
											message = "Cannot stop private messaging with a user that does not exist.";
											dos.println(message);
											dos.flush();
											continue;
										} else if(!writingSockets.keySet().contains(broken[1])){
											message = "Error. Cannot stop private messaging with a user who does not have P2P active.";
											dos.println(message);
											dos.flush();
											continue;
										}
										// send message on private TCP connection (get the right output printwriter)
										// if the connection exists

										String check = name + broken[1];
										if(whoInPrivate.keySet().contains(check)){
											Socket privateRecipient = (Socket) whoInPrivate.get(check);
											privateRecipient.close();
											message = "Private messaging with " + broken[1] + " has been terminated.";
											dos.println(message);
											dos.flush();
											whoInPrivate.remove(check);
										}else{
											message = "Error. Private messaging to " + broken[1] + " not enabled.";
											dos.println(message);
											dos.flush();
										}
									}
								}
							}catch (SocketTimeoutException e){
								// IF NO COMMAND FOUND THEN TIMEOUT (NO COMMAND ENTERED)
								//System.out.println("No input from " + name + " after timeout seconds");
								dos.println(timeout_message);
								dos.flush();
								time_out.add(serverRunTime);
								log_out_times.put(name, time_out);
								// timeout boolean is now true
								timed_out = true;
								// remove the user from online list and remove client
								online_users.remove(name);
								clients.remove(s);
								// remove by refferring to key
								users.remove(name);
								// send presence notification to rest of usernames
								message = name + " logged out.";
								presenceNotification(message);
							}catch (SocketException g){
								online_users.remove(name);
								clients.remove(s);
								// remove by referring to key
								users.remove(name);
								// send presence notification to rest of usernames
								message = name + " logged out.";
								presenceNotification(message);
								time_out.add(serverRunTime);
								log_out_times.put(name, time_out);
								timed_out = true;
							} catch (NoSuchElementException e){
									//System.out.println("Hashmap empty.");
							} catch (NumberFormatException e){
									message = "Please enter a valid number.";
									dos.println(message);
									dos.flush();
							}catch (Exception e){
									e.printStackTrace();
							}

							//System.out.println(online_users);
							//System.out.println(timed_out);
						}
					}
				}

				public void logIn(BufferedReader dis, PrintWriter dos) throws Exception{
					// return to client statements
					String success = "Welcome to CSESMS!";
					String failure_username = "Invalid username. Please try again.";
					String failure_password = "Invalid password. Please try again.";
					String blocked = "Your account has been blocked. Please try again later";
					String log_blocked = "Your account was blocked due to multiple login failures. Please try again later.";
					String user_already_logged = "This user is already logged in. Please log out of this account first.";
					int block = 0;
					String username = "";
					String password = "";
					//==================== CHECK FOR BLOCK ================================
					try{
						while(block < 3 && new_block == 0){
							username = dis.readLine();
							password = dis.readLine();
							// if the IP is blocked OR the username is blocked, deny connection
							if(!blockedClients.isEmpty()){
								if(blockedClients.contains(s.getInetAddress().getHostAddress())){
									//System.out.println(blockedClients.contains(s.getInetAddress()));
									//System.out.println(blockedUsernames[usernames.indexOf(username)]);
									//System.out.println(current_blocked_username);
									dos.flush();
									client.close();
									break;
								}
							}
							if(usernames.contains(username)){
								if(blockedUsernames[usernames.indexOf(username)] == 1){
									dos.println(log_blocked);
									dos.flush();
									//System.out.println(blockedUsernames[usernames.indexOf(username)]);
									//System.out.println(current_blocked_username);
									Thread.sleep(1000);
									client.close();
									break;
								}
								if(passwords.contains(password) && passwords.indexOf(password) == usernames.indexOf(username)){
									// if user already logged in, deny connection
									if(online_users.contains(username)){
										dos.println(user_already_logged);
										dos.flush();
										// allow another input by continuing loop
										continue;
									}
									//******* SUCCESSFULLY LOGGED IN **********
									// notify client
									dos.println(success);
									dos.flush();
									// store current name, and set boolean to true
									name = username;
									logged_in = true;
									// store online userdata
									online_users.add(name);
									break;
								}else{
									block++;
									if(block == 3) break;
									dos.println(failure_password + " " + (3 - block) + " attempts remaining.");
								}
							}else{
								dos.println(failure_username);
								dos.flush();
								continue;
							}

							dos.flush();
							//System.out.println(block);
						}
					}catch (SocketException e){
						//System.out.println("Client " + client + " disconnected");
						clients.remove(s);
						//System.out.println("Clients are: " + clients);
					}

					if(block == 3){
						// store current IP and username of client input
						current_blocked_ip = client.getInetAddress().getHostAddress();
						current_blocked_username = username;
						// add to blocked clients and usernames
						blockedClients.add(current_blocked_ip);
						blockedUsernames[usernames.indexOf(username)] = 1;
						// send message to client
						dos.println(blocked);
						dos.flush();
						// new block created, make new thread and start block duration timer
						new_block = 1;
						Thread b = new BlockHandler(blockedUsernames, blockedClients, block_duration, current_blocked_ip, current_blocked_username);
						b.start();
					}
				}
			}
}
