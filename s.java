public static void main(String[] args)throws Exception {
  /* define socket parameters, Address + PortNo, Address will default to localhost */
  Server server = new Server();
  SocketAddress sAddr;
  if(args.length != 3){
    System.out.println("Required arguments: Port, Block Duration, Timeout");
    return;
  }
  int serverPort = Integer.parseInt(args[0]);
  block_duration = Integer.parseInt(args[1]);
  timeout = Integer.parseInt(args[2]);
  /* change above port number if required */

  /*create server socket that is assigned the serverPort (6789)
      We will listen on this port for connection request from clients */
  welcomeSocket = new ServerSocket(serverPort);
  System.out.println("Server is ready :");
  // ****** GET USERNAMES AND PASSWORDS ********
  getCredentials("Credentials.txt");
  while (true){
      // accept connection from connection queue
      try{
        Server s = new Server();
        client = welcomeSocket.accept();
        sAddr =  client.getRemoteSocketAddress();
        BufferedReader dis = new BufferedReader(new InputStreamReader(client.getInputStream()));
        PrintWriter dos = new PrintWriter(new OutputStreamWriter(client.getOutputStream()));
        // make thread for new client
        System.out.println("Assigning new thread for this client.");
        Thread t = new Thread(s.new ClientHandler(client, dis, dos));
        //invoke run by starting Thread
        t.start();
      }
      catch (Exception e){
        e.printStackTrace();
      }
  }
}
