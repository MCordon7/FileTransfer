import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class MessengerWithFiles {
	/*
	 * Note: input/output is used for messaging 
	 * fin/fout is used for file transfers
	 */
	
	public class FileHandler implements Runnable {
		DataOutputStream output;
		DataInputStream input;
		Socket fSocket;
		ServerSocket fServer;
		String type, fileName, address;
		int port;

		public FileHandler(DataOutputStream output, DataInputStream input, Socket fSocket, 
						   String type, String address, int port) {
			this.output = output;
			this.input = input;
			this.fSocket = fSocket;
			this.type = type;
			this.address = address;
			this.port = port;
		}
		
		public FileHandler(DataOutputStream output, DataInputStream input, Socket fSocket, 
						   ServerSocket fServer, String type, String address, int port) {
			this.output = output;
			this.input = input;
			this.fSocket = fSocket;
			this.fServer = fServer;
			this.type = type;
			this.address = address;
			this.port = port;
		}
		
		@Override
		public void run() {
			try {
			if(type.equals("file-send")) {
				fSend();
			}
			if(type.equals("file-receive")) {
				fReceive();
			}
			fSocket.close();
			} catch(IOException e) {
				
			}
		}
		
		public void setFile(String fileName) {
			this.fileName = fileName;
		}
		
		public void fSend() throws IOException {
			//Resets the sockets so that we can send files
			if(fSocket.isClosed()) {
				resetSocket();
			}
			//Creates a new file to send
			File file = new File(fileName);
			//If the file exists and is readable, we can continue on
			if(file.exists() && file.canRead()) {
				output.writeLong(file.length());
				if(file.length() == 0) {
					if(fSocket.isConnected()) {
						fSocket.close();
					}
					return;
				}
				//Tells the receiver that the file exists so that they can also move forward
				output.writeBoolean(true);
				
				FileInputStream fileInput= new FileInputStream(file);
				byte[] buffer = new byte[1500];
				int numRead;
				//Takes the data from our requested file and sends it to the source that requested it
				while((numRead = fileInput.read(buffer)) != -1) {
					output.write(buffer, 0, numRead);
				}
				fileInput.close();
				if(fSocket.isConnected()) {
					fSocket.close();
				}
			} else {
				//If the files don't exist, we tell the receiver it cannot get a file
				//We also delete the created file from earlier
				output.writeBoolean(false);
				file.delete();
			}
		}
		
		public void fReceive() throws IOException{
			//Resets the sockets so that we can send files
			if(fSocket.isClosed()) {
				if(fSocket.isConnected()) {
					fSocket.close();
				}
				resetSocket();
			}
			//Sender thread tells us if we can receive the file
			long fileSize = input.readLong();
			if(fileSize == 0) {
				return;
			}
			boolean canReceive = input.readBoolean();
			if(canReceive) {
				FileOutputStream fileOutput = new FileOutputStream(fileName);	
				byte[] buffer = new byte[1500];
				int numRead;
				//Writes our requested data to the directory
				while((numRead = input.read(buffer)) != -1) {
					fileOutput.write(buffer, 0, numRead);
				}
				fileOutput.close();
				if(fSocket.isConnected()) {
					fSocket.close();
				}
			}
		}
		
		private void resetSocket() throws IOException {
			//If the thread is from the server, we can just call the file server socket to make the new file socket
			if(fServer != null) {
				fSocket = fServer.accept();
			} else {
				//If the thread is from the client, we can make a new file socket
				fSocket = new Socket(address, port);
			}
			//Creates the new input/output streams for file transferring
			input = new DataInputStream(fSocket.getInputStream());
			output = new DataOutputStream(fSocket.getOutputStream());
		}
		
	}

	//Allows threads to send and recieve messages
	public class MessageHandler implements Runnable {
		FileHandler fSender, fReceiver;
		DataOutputStream output, fout;
		DataInputStream input, fin;
		BufferedReader reader;
		ServerSocket fileServer;
		Socket cSocket, fSocket;
		String address, type, fileName, userInput;
		int filePort;
		boolean messengerMode = false;

		//Constructor for the server
		public MessageHandler(DataOutputStream output, DataInputStream input, DataOutputStream fout, DataInputStream fin,
				Socket socket,Socket fSocket, ServerSocket fileServer, int filePort, String address, String type) {
			reader = new BufferedReader(new InputStreamReader(System.in));
			this.output = output;
			this.input = input;
			this.cSocket = socket;
			this.fout = fout;
			this.fin = fin;
			this.fSocket = fSocket;
			this.fileServer = fileServer;
			this.filePort = filePort;
			this.address = address;
			this.type = type;
			fSender = new FileHandler(fout,fin,fSocket,fileServer,"file-send",address,filePort);
			fReceiver = new FileHandler(fout,fin,fSocket,fileServer,"file-receive",address,filePort);
		}

		//Constructor for the client
		public MessageHandler(DataOutputStream output, DataInputStream input, DataOutputStream fout, DataInputStream fin,
				Socket socket,Socket fSocket,int filePort, String address, String type) {
			reader = new BufferedReader(new InputStreamReader(System.in));
			this.output = output;
			this.input = input;
			this.cSocket = socket;
			this.fout = fout;
			this.fin = fin;
			this.fSocket = fSocket;
			this.filePort = filePort;
			this.address = address;
			this.type = type;
			fSender = new FileHandler(fout,fin,fSocket,"file-send",address,filePort);
			fReceiver = new FileHandler(fout,fin,fSocket,"file-receive",address,filePort);
		}

		@Override
		public void run() {
			if(type.equals("send")) {
				send();
			}
			if(type.equals("receive")){
				receive();
			}
			System.exit(0);
		}

		public void send() {
			try {
				// input the message from standard input
				String message;
				System.out.println("Enter an option ('m', 'f', 'x'):");
				System.out.println("    (M)essage (send)");
				System.out.println("    (F)ile (request)");
				System.out.println("   e(X)it");
				while((message  = reader.readLine()) != null) {
					output.writeUTF(message);
					if(message.equals("m")) {
						System.out.println("Enter your message:");
						output.writeUTF(reader.readLine());
					}
					if(message.equals("f")){
						System.out.println("Which file do you want?");
						this.fileName = reader.readLine();
						output.writeUTF(this.fileName);
						fReceiver.setFile(this.fileName);
						Thread receiverFileThread = new Thread(fReceiver);
						receiverFileThread.start();
					}
					if(message.equals("x")) {
						System.out.println("closing your sockets...goodbye");
						if(!(cSocket.isClosed())) {
							cSocket.close();
						}
						if(!(fSocket.isClosed())) {
							fSocket.close();
						}
						System.exit(0);
					}
					System.out.println("Enter an option ('m', 'f', 'x'):");
					System.out.println("    (M)essage (send)");
					System.out.println("    (F)ile (request)");
					System.out.println("   e(X)it");
				}
			} catch(EOFException eof) {	
				//If one side shuts down, we can shut down too
				System.exit(0);
			} catch(Exception e) {
				System.out.println(e.getMessage());
			}
		}

		public void receive() {
			try {
				// input the message from standard input
				String message;
				while((message = input.readUTF()) != null) {
					if(message.equals("m")) {
						System.out.println(input.readUTF());
					}
					if(message.equals("f")) {
						this.fileName = input.readUTF();
						fSender.setFile(this.fileName);
						Thread sendFileThread = new Thread(fSender);
						sendFileThread.run();
					}
					if(message.equals("x")) {
						if(!(cSocket.isClosed())) {
							cSocket.close();
						}
						if(!(fSocket.isClosed())) {
							fSocket.close();
						}
						System.exit(0);
					}
				}
			} catch(EOFException eof) {
				//If one side shuts down, we can shut down too
				System.exit(0);
			} catch(Exception e) {
				System.out.println(e.getMessage());
			}
		}
	}

	public void runServer(int port) {
		try {
			//Init sockets for messaging
			ServerSocket sSocket= new ServerSocket(port);
			Socket cSocket= sSocket.accept();

			DataOutputStream output = new DataOutputStream(cSocket.getOutputStream());
			DataInputStream input = new DataInputStream(cSocket.getInputStream());	

			//Init sockets for file transfers
			int filePort = input.readInt();
			String address = input.readUTF();
			ServerSocket fsSocket = new ServerSocket(filePort);
			Socket fSocket = fsSocket.accept();
			DataOutputStream fout = new DataOutputStream(fSocket.getOutputStream());
			DataInputStream fin = new DataInputStream(fSocket.getInputStream());	

			//Init MessageHandler
			MessageHandler send = new MessageHandler(output, input, fout, fin, cSocket, fSocket, fsSocket, filePort, address, "send");
			MessageHandler receive = new MessageHandler(output, input, fout, fin, cSocket, fSocket, fsSocket, filePort, address,"receive");


			//Init threads
			Thread senderThread = new Thread(send);
			Thread receiverThread = new Thread(receive);


			//Start threads
			senderThread.start();
			receiverThread.start();
		} catch(EOFException eof) {
			System.out.println("EOF encountered; other side shut down");
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}
	}

	public void runClient(int filePort, int port, String address) {
		try {
			//Init sockets for messaging
			Socket cSocket= new Socket(address, port);
			DataOutputStream output = new DataOutputStream( cSocket.getOutputStream() );
			DataInputStream input = new DataInputStream( cSocket.getInputStream() );
			output.writeInt(filePort);
			output.writeUTF(address);

			//Init sockets for file transfers
			Socket fSocket = new Socket(address, filePort);
			DataOutputStream fout = new DataOutputStream( fSocket.getOutputStream() );
			DataInputStream fin = new DataInputStream( fSocket.getInputStream() );

			//Init MessageHandler
			MessageHandler send = new MessageHandler(output, input, fout, fin, cSocket, fSocket,filePort, address, "send");
			MessageHandler receive = new MessageHandler(output, input, fout, fin, cSocket, fSocket, filePort, address,"receive");


			//Init threads
			Thread senderThread = new Thread(send);
			Thread receiverThread = new Thread(receive);

			//Start threads
			senderThread.start();
			receiverThread.start();
		} catch(EOFException eof) {
			System.out.println("EOF encountered; other side shut down");
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}
	}

	public static void main(String[] args) {
		MessengerWithFiles messenger = new MessengerWithFiles();
		//Default address
		String address = "localhost";

		if(args.length == 2) {
			messenger.runServer(Integer.valueOf(args[1]));
		} else {
			if(args.length == 6 && args[4] != null){
				address = args[5];
			}
			messenger.runClient(Integer.valueOf(args[1]), Integer.valueOf(args[3]), address);
		}

	}
}

