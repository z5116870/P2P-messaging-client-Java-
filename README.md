# P2P-messaging-client-Java-
Refer to the report.pdf file to see the how the messaging client works works. 

**Usage**:
Run server using the following usage: java Server [portNo] [userBlockDuration] [userTimeoutDuration]
e.g. java Server 8000 10 100
Where:
  portNo is the the port number on the localhost that you want to server to recieve messsages on
  userBlockDuration is the length of time in seconds a user can block another user for
  userTimeoutDuration is the length of time in seconds a user will automatically be logged out after not initiating any commands
  
 Run the clients using the following usage: java Client 127.0.0.1 [portNo] (If running on localhost)
 e.g. java Client 127.0.0.1 8000
 
 **What Clients can do**:
 All registered users are stored in the credentials.txt file.
 Messages sent to users that arent currently online will be stored, and the relevant user will be notified upon logging in, with a "You have pending messages!" notification. 
 There can be any number of clients.
 
 Clients can issue any of the following commands:
  1. whoelse - lists the usernames of all other users currently logged in.
  2. message [username] [message] - sends a [message] to [username] user, only they can see.
  3. broadcast [message] - broadcast a [message] to all users currently logged in. This command will make sure your message is seen by everyone. Broadcasted messages are not stored for users not logged in. 
  4. block [username] - blocks the user [username], they cannot send messages to you.
  5. unblock [username] - unblocks the user [username], they can once again send messages to you.
  6. whoelsesince [time] - lists all users that have logged in since the last [time] seconds.
  7. logout - logs out the current user.
  8. startprivate [username] - start a private connection with the user [username] so messages can be sent and recieved more securely.
  9. private [username] [message] - send a [message] to user [username] across a connection created with startprivate.
  10. stopprivate [username] - stops the private connection with user [username].
