import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

/**
   This class handles the communication via chat between teh professor and the student.  
   each student has a private chat witht the professor.
   The professor also has the capability to send a broadcast message to every client.
   @author Nathaniel Larrimore
   Team 2: Team Nineteen
*/

public class ServerGUI extends JFrame implements Runnable{

   private JPanel jpMain;/**JPanel that holds eveything*/
   private JList<String> users;/**JList to display the users that are connected*/
   private JButton send;/**button to send the text*/
   private JTextField sendText;/**Field to hold the text you wish to send*/
   private JTextArea receiveText;/**Field to hold the all previously sent messages*/
   private JPanel textPanel;/**JPanel to hold the sendText and send button*/
   private JScrollPane scrollPane,scrollReceive;/** */
 
   private Vector<String> connectedClients = new Vector<String>();/**Holds all the names of the connected clients*/
   private Vector<String> unAnsweredClients = new Vector<String>();/**holds all the names of clients waiting for a response*/
   private Hashtable<String, JTextArea> userPanes = new Hashtable<String,JTextArea>();/**Holds the name of the client, and their particular chat history*/
   private String currentDisplayed = null;/**Is the user sending a message displayed*/
   private boolean adding = false;/**used for making a change to the JList*/
   private boolean sending = false;/**used for the server sending a message*/
   private MainServer.Server comm;/**the comunication portion of the server*/
 
   private String name;/**name of the professor*/
   private String pass;/**Password for the server*/
   private String address = "localhost";/**address to connect to*/
   private int port = 16789;/**port to bind on*/
   private Socket s = null;/**socket to send over*/
   private ObjectOutputStream  out = null;/**stream used to send*/
   private ObjectInputStream in = null;/**stream used to recieve*/
   private static final long serialVersionUID = 42L;
   
   private JLabel IP; /**IP address for the host machine to be displayed*/
   private CountDown remainingTime;/**Count down timer for how much time is left in the exam*/
   private JLabel infinity = new JLabel("\u221E");/**Infinity symbol*/
   private JPanel time;/**Panel to hold the IP address and time labels*/
   private Font headerFont = new Font(null,Font.BOLD + Font.ITALIC,24);/**Font for the main labels*/
   private Font subFont = new Font(null,Font.BOLD + Font.ITALIC,18);/**Font for the sub labels*/
   private Color fontColor = new Color(0,0,255);/**Color for the labels*/

   /**
   @param _comm the MainServer.Server for the communication between the professor and students
   @param _name Name of the professor
   @param pass the set password by the professor
   */
   public ServerGUI(MainServer.Server _comm,String _name,String pass){
      name = _name;
      comm = _comm;
      this.pass = pass;
      
      jpMain = new JPanel();
      jpMain.setLayout(new BorderLayout());   
      try{
         IP = new JLabel();
         IP.setText("  " + InetAddress.getLocalHost().getHostAddress());
      }
      catch(UnknownHostException uhe){
         uhe.printStackTrace();
      }
      //add a menu bar
      JMenuBar jmbar = new JMenuBar();
      JMenu jmFile = new JMenu("File");
      JMenuItem jmiFileQuit = new JMenuItem("Quit");
      jmFile.add(jmiFileQuit);
      jmbar.add(jmFile);
            
      JMenu jmAbout = new JMenu("About");
      JMenuItem jmiEditAbout = new JMenuItem("About");
      jmAbout.add(jmiEditAbout);
      jmbar.add(jmAbout);
      setJMenuBar(jmbar);
         
      jmiFileQuit.addActionListener(
         new ActionListener(){
            public void actionPerformed(ActionEvent ae){
               System.exit(0);
            }
         });
         
      jmiEditAbout.addActionListener(
         new ActionListener(){
            public void actionPerformed(ActionEvent ae){
               JOptionPane.showMessageDialog(null,"Version: 1.0\nAuthors: Nathaniel Larrimore, Niki Genung, Brendon Strowe\nRelease: May 19, 2016\n\nThis program helps students submit their files during an exam and help facilitate communication between the professor and student(s).\nThe Professor can specify their name, a directory they wish to save all the submitted files to, the time they wish the exam to end, and a password.");
            }
         });
      
      //list of connected users      
      users = new JList<String>();
      
      scrollPane = new JScrollPane();
      scrollPane.getViewport().add(users);
      jpMain.add(scrollPane, BorderLayout.WEST );
      
      users.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      users.setSize(new Dimension(200,100));
      
      //selection listener for JList
      users.addListSelectionListener(
         new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent event)
            {
               if(!adding){
                  updateScreen();
               
               }
            }
         });
   
      //send button
      send = new JButton("Send");
      
      //send messages
      sendText = new JTextField(70);
      sendText.setBorder(new EtchedBorder());
      
      //receive messages    
      receiveText = new JTextArea(30,30);  
      receiveText.setBorder(new EtchedBorder());
      receiveText.setLineWrap(true);
      receiveText.setWrapStyleWord(true);
      receiveText.setEditable(false);
      scrollReceive = new JScrollPane(receiveText);
      
      //jpanel for the sending portion of the GUI
      JPanel sendPanel = new JPanel(new BorderLayout());
      sendPanel.add(sendText,BorderLayout.WEST);
      sendPanel.add(send,BorderLayout.EAST);
      
      textPanel = new JPanel(new BorderLayout());
      textPanel.add(scrollReceive,BorderLayout.CENTER);
      textPanel.add(sendPanel,BorderLayout.SOUTH);
      
      jpMain.add(textPanel,BorderLayout.CENTER);
      //action listener for send button   
      send.addActionListener(
         new ActionListener(){
            public void actionPerformed(ActionEvent ae)
            {
               String msg = sendText.getText();
               sendOut(msg);
               sendText.setText(null);
            
            }
         });
      //key listener so when user hits enter the message is sent   
      sendText.addKeyListener(
         new KeyAdapter(){
            public void keyPressed(KeyEvent ke)
            {               
               if(ke.getKeyCode() == KeyEvent.VK_ENTER)
               {
                  send.doClick();
               }   
            }
         });
   
      addWindowListener(
         new WindowAdapter(){
            public void windowClosed(ActionEvent ae)
            {
               sendOut("quit");
               for(int i = 0;i < comm.clients.size();i++){
                  comm.clients.get(i).sendOut("END","");
               }
               disconnect();
               System.exit(0);
            
            }
         });
         
         
      add(jpMain);
      pack();
      setDefaultCloseOperation(EXIT_ON_CLOSE);
      setLocationRelativeTo(null);
      setVisible(true);
   }
   /**
   check to see if a particular clinet's chat is being displayed at that moment
   @param name Name of client you wish to see is currently being displayed or not
   @return return true if user is displayed, return false if they aren't
   */
   public boolean isUserDisplayed(String name){
      adding = true;
      if(!users.isSelectionEmpty()){
         if(users.getSelectedValue().equals(name) || users.getSelectedValue().equals("*" + name)){
            adding = false;
            return true;
         }
         else{
         
            adding = false;
            return false;      
         }
      }
      else{
         
         adding = false;
         return false;
      }
      
   }
   
   public void addWaitingClient(String name){
      if(!sending && !comm.getClientName(name).isFirst()){
         adding = true;
         unAnsweredClients.add(name);
         connectedClients.remove(name);
         connectedClients.add(0,"*" + name);
         users.setListData( connectedClients );
         scrollPane.revalidate();
         scrollPane.repaint();
         adding = false;
      }
   }
   
   
   /**
   update the screen with a new chat message
   */
   public void updateScreen(){
      adding = true;
      String currentUser = users.getSelectedValue().substring(1);
      
      if(users.getSelectedValue().substring(0,1).equals("*")){
         JTextArea work = userPanes.get(currentUser);
         receiveText.setText(work.getText());
         userPanes.put(currentUser,work);
         receiveText.setCaretPosition(receiveText.getDocument().getLength());
         
         jpMain.repaint();
         
         unAnsweredClients.remove(users.getSelectedValue());
         
         unAnsweredClients.remove(currentUser);
         int location = connectedClients.indexOf(users.getSelectedValue());
         connectedClients.remove(users.getSelectedValue());
         connectedClients.add(location,currentUser);
         users.setListData( connectedClients );
         scrollPane.revalidate();
         scrollPane.repaint();
         users.setSelectedIndex(connectedClients.indexOf(currentUser));
         adding = false;
      }
      else{
         JTextArea work = userPanes.get(users.getSelectedValue());
         receiveText.setText(work.getText());
         userPanes.put(users.getSelectedValue(),work);
         receiveText.setCaretPosition(receiveText.getDocument().getLength());
         
         jpMain.repaint();
      }
      adding = false;
   }
   
   /**
   Add a client to the list of connected clients
   @param _name of the user to be added
   @param userArea JTextArea to hold the chat history for this client
   */
   public void addClient(String _name,JTextArea userArea){
      adding = true;
      connectedClients.add(_name);
      userPanes.put(_name,userArea);
   	
      
      users.setListData( connectedClients );
      scrollPane.revalidate();
      scrollPane.repaint();
      adding = false;
   }
   /**
   Remove a client from list of connected clients
   @param _name of the user to be removed
   @param userArea JTextArea to hold the chat history for this client
   */
   public void removeClient(String _name,JTextArea userArea){
      adding = true;
      connectedClients.remove(_name);
      userPanes.remove(_name,userArea);
      
      
      users.setListData( connectedClients );
      scrollPane.revalidate();
      scrollPane.repaint();
      adding = false;
   }
   /**
   send message to particular client
   @param msg String to be sent from the server
   */
   public void sendOut(String msg){
      String tempName;
      String regex = "\\s*\\bBROADCAST\\b\\s*";
      tempName = name.replaceAll(regex,"");
   
      if(!users.isSelectionEmpty()){   
         if(users.getSelectedValue().equals(name)){
            sendAll(msg);
         }
         else{
            comm.getClientName(users.getSelectedValue()).sendOut(tempName + ":",msg);
         }
      }
   }
   /**
   Send a message to all connected clients
   @param msg Messge to be sent
   */
   public void sendAll(String msg){
      sending = true;
      for(int i = 0;i < comm.clients.size();i++){
         comm.clients.get(i).sendOut(name + ":",msg);
      }
      sending = false;
   }
   /**
   Disconnect and end the exam
   */
   public void disconnect(){
      try{
         in.close();
         out.close();
         s.close();
      }
      catch(IOException ioe){
         ioe.printStackTrace();
      }  
   }
   
   public void addCountDown(Long timer){
      time = new JPanel(new GridLayout(1,0));
      
      JLabel currTime = new JLabel("Current time:  ",JLabel.RIGHT);
      JLabel timeLeft = new JLabel("Time left:  ",JLabel.RIGHT);
      JLabel yourIP = new JLabel("Your IP:",JLabel.RIGHT);
      
      currTime.setFont(subFont);
      currTime.setForeground(fontColor); 
      
      timeLeft.setFont(subFont);
      timeLeft.setForeground(fontColor); 
      
      yourIP.setFont(subFont);
      yourIP.setForeground(fontColor);
      
      IP.setFont(headerFont);
      IP.setForeground(fontColor); 
      
      infinity.setFont(headerFont);
      infinity.setForeground(fontColor);
       
      time.add(yourIP);
      time.add(IP);
      time.add(currTime);
      
      Clock clock = new Clock();
      Thread th = new Thread(clock);
      th.start();
      
      time.add(clock);
      
      time.add(timeLeft);
         
      if(timer != 0){
         Calendar cal = Calendar.getInstance();
         System.out.println("" + timer);
         System.out.println("" + cal.getTimeInMillis());
                     
                     
         remainingTime = new CountDown((timer - cal.getTimeInMillis()));
                     
         Thread th2 = new Thread(remainingTime);
                     
         th2.start();
         time.add(remainingTime);
      }
      else{               
         time.add(infinity);
      }
      jpMain.add(time,BorderLayout.NORTH); 
      pack();        
   }
   
   /**
   Start the communication portion of the GUI
   */
   public void run(){
      try
      {
      
         s = new Socket(address,port);
         out = new ObjectOutputStream(s.getOutputStream());
         out.flush();
         in = new ObjectInputStream(s.getInputStream());
      
         
      }
      catch(UnknownHostException uhe) 
      {
         System.out.println("Unable to connect to host.");
      }
      catch(IOException ie) 
      {
         System.out.println("Unable to connect to host.");
      }
      
      try
      {
         out.writeObject(pass);
         out.flush();
         in.readObject();
         out.writeObject(name);
         out.flush();
      }
      catch(IOException ioe)
      {
         ioe.printStackTrace();
      }
      catch(ClassNotFoundException cnf){
         cnf.printStackTrace();
      }
   }
   
   class Clock extends JLabel implements Runnable
   {
      private static final long serialVersionUID = 42L;
      public void run()
      {
         setFont(headerFont);
         setForeground(fontColor);         
         new javax.swing.Timer(1000,
               new ActionListener(){
                  public void actionPerformed(ActionEvent ae)
                  {
                     setText(new SimpleDateFormat("hh:mm:ss a").format(new Date()));
                  
                  }
               }).start();
                      
      }
   }
      
   class CountDown extends JLabel implements Runnable{
         
      private static final long serialVersionUID = 42L;
      private long timeLeft;
      private javax.swing.Timer timer;
         
      public CountDown(long _timeLeft){
         timeLeft = _timeLeft / 1000;
      }
      public void run(){
         setFont(headerFont);
         setForeground(fontColor);         
         timer = new javax.swing.Timer(1000,
               new ActionListener(){
                  public void actionPerformed(ActionEvent ae){
                     timeLeft--;
                     int seconds = (int)timeLeft % 60;
                     int hours = (int)timeLeft / 3600;
                     int minutes = (int)(timeLeft - (hours * 3600)) / 60;
                     String sSeconds = "" + seconds;
                     String sHours = "" + hours;
                     String sMinutes = "" + minutes;
                     
                     if(seconds < 10){
                        sSeconds = "0" + seconds;
                     }
                     if(hours < 10){
                        sHours = "0" + hours;
                     }
                     if(minutes < 10){
                        sMinutes = "0" + minutes;
                     }
                     
                     setText(sHours + ":" + sMinutes + ":" + sSeconds);
                     //setText(new SimpleDateFormat("hh:mm:ss a").format(timeLeft));
                     if(timeLeft <= 0){
                        try{
                           in.close();
                           out.close();
                           s.close();
                        }
                        catch(IOException ioe){
                           ioe.printStackTrace();
                        }
                        
                        JOptionPane.showMessageDialog(null,"Exam has ended: Ran out of Time.");
                        end();
                     }
                  }
               });
         timer.start();
      }
      public void end(){
         timer.stop();
      }
   
   
   
   }
}