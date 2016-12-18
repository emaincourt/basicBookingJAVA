package model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Date;
import java.sql.*;

/**
 * Provides the application with high-level methods to access the persistent
 * data store. The class hides the fact that data is stored in a RDBMS and it
 * also hide all the complex SQL machinery required to access it.
 * <p>
 * The constructor and the methods of this class all throw a
 * {@link DataAccessException} whenever an unrecoverable error occurs.
 * <b>Beware</b>: constraint violations are <b>not</b> considered unrecoverable.
 *
 * @author Jean-Michel Busca
 */
public class DataAccess {

  // To keep the emthods' interface simple, we assume there are only two
  // price classes: child and adult.
  public static int CHILD = 1;
  public static int ADULT = 2;
  public static int CHILD_PRICE = 25;
  public static int ADULT_PRICE = 50;
  
  Connection conn = null;
  PreparedStatement ps = null;
  ResultSet rs = null;
  
  public static void main(String[] args) throws DataAccessException, ClassNotFoundException, SQLException{
    
    DataAccess myDataAccess = new DataAccess("jdbc:mysql://localhost:8889/booking","root","root");
    BookingInfo bi2 = myDataAccess.book("Yi-Han", 2, 2, true);
    BookingInfo bi3 = myDataAccess.book("Samuel", 0, 3, true);
    //BookingInfo bi4 = myDataAccess.cancel("Samuel", 1, 1);
    //BookingInfo bi5 = myDataAccess.cancel("Yi-Han", 0, 1);
    //BookingInfo bi6 = myDataAccess.cancel("Elliot", 1, 0);
    System.out.println(myDataAccess.getBookingInfo("Yi-Han").toString());
    myDataAccess.close();
  }
  /**
   * Creates a new <code>DataAccess</code> object that itneracts with the
   * specified database, using the specified login and password. Each object
   * maintains a
   * <b>dedicated</b> connection to the database until the {@link close} method
   * is called.
   *
   * @param url the url of the database to connect to
   * @param login the (application) login to use
   * @param password the password
   * @throws DataAccessException if an unrecoverable error occurs
   * @throws java.lang.ClassNotFoundException
   * @throws java.sql.SQLException
   */
  public DataAccess(String url, String login, String password) throws DataAccessException, ClassNotFoundException, SQLException {
    try {        
        Class.forName("com.mysql.jdbc.Driver" );
        this.conn = DriverManager.getConnection(url, login, password );
        System.out.println("Connection established.");
        this.createTriggerBeforeBooking();
        System.out.println("Triggers Created.");
        this.getPrices();
        System.out.println("Prices fetched.");
    }
    catch (ClassNotFoundException e){
        System.out.println("Connection driver Class not found.");
    }
    catch (SQLException e){
        System.out.println("Unable to connect to DB.");
    }
  }
  
  public void getPrices() throws SQLException{
      try{
        ps = this.conn.prepareStatement("SELECT * FROM PRICES;");
        rs = ps.executeQuery();
        
        while(rs.next())
        {
            if(rs.getInt(1) == CHILD)
                CHILD_PRICE = rs.getInt(2);
            else if(rs.getInt(1) == ADULT)
                ADULT_PRICE = rs.getInt(2);
        }
        if(ps != null)
            ps.close();
        if(rs != null)
            rs.close();
      }catch(SQLException e){
          System.out.println("SQL error raised during prices fetch.");
      }
  }
  
  public void createTriggerBeforeBooking() throws SQLException{
      try{
        String addCustomer = "CREATE TRIGGER `before_booking_update` BEFORE UPDATE ON `BOOKINGS` FOR EACH ROW BEGIN IF (SELECT COUNT(*) FROM `ORDERS` WHERE CUSTOMER = NEW.CUSTOMER) = 0 AND NEW.CUSTOMER IS NOT NULL THEN INSERT INTO `ORDERS` VALUES (NEW.CUSTOMER,0,NOW()); END IF; END";
        String updateCustomerAmount = "CREATE TRIGGER `after_booking_update` AFTER UPDATE ON `BOOKINGS` FOR EACH ROW BEGIN IF NEW.CUSTOMER IS NOT NULL THEN UPDATE ORDERS SET AMOUNT = (SELECT PRICE FROM PRICES WHERE CLASS = 1)*(SELECT COUNT(*) FROM BOOKINGS WHERE CLASS = 1 AND CUSTOMER = NEW.CUSTOMER)+(SELECT PRICE FROM PRICES WHERE CLASS = 2)*(SELECT COUNT(*) FROM BOOKINGS WHERE CLASS = 2 AND CUSTOMER = NEW.CUSTOMER) WHERE CUSTOMER = NEW.CUSTOMER; END IF; END;";
        ps = this.conn.prepareStatement(addCustomer);
        ps.execute();
        if(ps != null)
            ps.close();
        ps = this.conn.prepareStatement(updateCustomerAmount);
        ps.execute();
        if(ps != null)
            ps.close();
      }catch(SQLException e){
          System.out.println("Unable to create trigger.");
      }
  }
  /**
   * Books the specified number of seats for the specified customer. The number
   * of seats is specified for each price class, in order to compute the total
   * amount of the booking. In addition, the customer can require that the
   * booked seats be grouped, i.e. they bear consecutive numbers. The booking is
   * performed in a all or nothing fashion.
   *
   * @param customer the customer who makes the booking
   * @param childCount the number of seats to book for children
   * @param adultCount the number of seats to book for adults
   * @param groupedSeats <code>true</code> if the booked seats must be grouped,
   * and <code>false</code> otherwise
   * @return a booking info object if the booking was successful, or
   * <code>null</code> if one of the booking criterion could not be satisfied
   * @throws DataAccessException if an unrecoverable error occurs
   * @throws java.sql.SQLException
   */
  public BookingInfo book(String customer, int childCount, int adultCount, boolean groupedSeats) throws DataAccessException, SQLException {
    
    try{
        ArrayList <Integer> seatsTable = getAvailableSeats();
        
        if(seatsTable == null){
            System.out.println("Aucun siège disponible dans les conditions établies.");
            return null;
        }
        
        int amount = childCount * CHILD_PRICE + adultCount * ADULT_PRICE;
        Date today = new java.util.Date();
        
        if(groupedSeats){
            int startIndex = findIndexForGroupedSeats(childCount,adultCount,seatsTable);
        
            if(startIndex == -1)
                return null;
        
            for(int i = 0; i < childCount; i++)
                insertEntry(startIndex+i,CHILD,customer);
            for(int i = 0; i < adultCount; i++)
                insertEntry(startIndex+childCount+i,ADULT,customer);
            
            BookingInfo booking = new BookingInfo(customer,amount,today,seatsTable);
            return booking;
        }
        else{
            if(seatsTable.size() >= childCount+adultCount){

                Iterator <Integer> it = seatsTable.iterator();

                int counter = 0;

                while(it.hasNext() && counter < adultCount){
                    insertEntry(it.next(),ADULT,customer);
                    counter ++;
                }

                while(it.hasNext() && counter < adultCount + childCount){
                    insertEntry(it.next(),CHILD,customer);
                    counter ++;
                }
                
                BookingInfo booking = new BookingInfo(customer,amount,today,seatsTable);
                return booking;
            }
        } 
    }catch(SQLException e){
        System.out.println("SQL error, unable to update data.");
    }
    return null;
  }
  
  public int findIndexForGroupedSeats(int childCount, int adultCount, ArrayList seatsTable){
      int index = -1;
      int counter = 0;
      int i;
      int neededSeats = childCount + adultCount;
      int prev = 0;
      
      Iterator <Integer> it = seatsTable.iterator();
      
      while(it.hasNext() && counter != neededSeats){
          
        i = it.next();
        
        if(index == -1){
            index = i;
            counter ++;
        }
        else{
            if(i == prev + 1){
                counter ++;
            }
            else{
                counter = 0;
                index = -1;
            }
        }
        prev = i;
      }
      return index;
  }
  
  public void insertEntry(int seat, int cl, String customer) throws SQLException{
    try{
        String insertSeatQuery = "UPDATE BOOKINGS SET CLASS = ?, CUSTOMER = ? WHERE SEAT = ?;";
        ps = this.conn.prepareStatement(insertSeatQuery);
        
        ps.setInt(1,cl);
        ps.setString(2,customer);
        ps.setInt(3,seat);
        ps.executeUpdate();
        
        ps.close();
        
    }catch(SQLException e){
        System.out.println("Unable to update field.");
    }
  }
  
  /**
   * Cancel, in whole or part, a previous booking made by the specified
   * customer. The cancellation specifies the number of seats to cancel in each
   * price class.
   *
   * @param customer the customer who cancel the booking
   * @param childCount the number of child seats to cancel, -1 if all are to be
   * cancelled
   * @param adultCount the number of child seats to cancel, -1 if all are to be
   * cancelled
   *
   * @return a new booking info object if the cancellation was successful, or
   * <code>null</code> if one of the parameter was incorrect
   * @throws DataAccessException if an unrecoverable error occurs
   */
  public BookingInfo cancel(String customer, int childCount, int adultCount) throws DataAccessException {
        try{
        
        if(customer==null || childCount<-1 || adultCount<-1) return null;
        int refund = childCount * 25 + adultCount * 50;
        int amount = 0;

        String getAmount = "SELECT AMOUNT FROM ORDERS WHERE CUSTOMER=?";
        ps = this.conn.prepareStatement(getAmount);
        ps.setString(1,customer);
        rs = ps.executeQuery();
        
        while(rs.next())
            amount = rs.getInt(1);
        if(rs!=null)    rs.close(); 
        if(ps!=null)    ps.close();

        
        
        if(amount<refund){
            System.out.println("Vous ne pouvez être remboursé plus que ce que vous avez payé.");
            return null;
        }
        
        String removeChildSeatsQuery = null;
        if(childCount==-1){
        removeChildSeatsQuery = "DELETE from BOOKINGS"
                + " where CLASS=? AND CUSTOMER=?"; 
        }else{
        removeChildSeatsQuery = "DELETE FROM BOOKINGS "
                + "WHERE SEAT IN (SELECT cid FROM "
                + "(SELECT SEAT as cid FROM BOOKINGS WHERE CLASS=? AND CUSTOMER=?)"
                + " as C )"
                + "LIMIT ?";
        }
             
        ps = this.conn.prepareStatement(removeChildSeatsQuery);
        ps.setInt(1, 1);
        ps.setString(2, customer);
        if(childCount!=-1) ps.setInt(3, childCount);
        ps.executeUpdate();
        if(ps!=null)    ps.close();
        
            
        String removeParentSeatsQuery = null;
        if(adultCount==-1){
        removeParentSeatsQuery = "DELETE from BOOKINGS"
                + " where CLASS=? AND CUSTOMER=?";
        }else{
        removeParentSeatsQuery ="DELETE FROM BOOKINGS "
                + "WHERE SEAT IN (SELECT cid FROM "
                + "(SELECT SEAT as cid FROM BOOKINGS WHERE CLASS=? AND CUSTOMER=?)"
                + " as C )"
                + "LIMIT ?";
;
        }
        ps = this.conn.prepareStatement(removeParentSeatsQuery);
        ps.setInt(1, 2);
        ps.setString(2, customer);
        if(adultCount!=-1) ps.setInt(3, adultCount);
        ps.executeUpdate();
        if(ps!=null)    ps.close();
        
        String updateOrdersQuery = "UPDATE ORDERS SET AMOUNT=AMOUNT-? WHERE CUSTOMER=?";
        ps = this.conn.prepareStatement(updateOrdersQuery);
        ps.setInt(1,refund);
        ps.setString(2,customer);
        ps.executeUpdate();
        if(ps!=null)    ps.close();
                    
        Date today = new java.util.Date();
        int total = amount - refund;
        
        ArrayList <Integer> seatsList = new ArrayList <> ();
        String getTable = "SELECT SEAT FROM BOOKINGS WHERE CUSTOMER=?";
        ps = this.conn.prepareStatement(getTable);
        ps.setString(1,customer);
        rs = ps.executeQuery();
        
        while(rs.next()){
            seatsList.add(rs.getInt(1));
        }
        
        if(rs!=null) rs.close(); 
        if(ps!=null)    ps.close();

        BookingInfo booking = new BookingInfo(customer,total,today,seatsList);
        return booking;
        
    }catch(SQLException e){
        System.out.println("Unable to update field at Samuel.");
    }
      return null;
  }

  /**
   * Closes the underlying connection and releases all related ressources. The
   * application must call this method when it is done accessing the data store.
   *
   * @throws DataAccessException if an unrecoverable error occurs
   */
  public void close() throws DataAccessException, SQLException {
    try { 
        if (rs != null) 
            rs.close(); 
    } catch (SQLException e) {
        System.out.println("Unable to close ResultSet.");
    };
    try { 
        if (ps != null) 
            ps.close(); 
    } catch (SQLException e) {
        System.out.println("Unable to close PreparedStatement.");
    };
    try { 
        if (conn != null) 
            conn.close(); 
    } catch (SQLException e) {
        System.out.println("Unable to close Connection.");
    };
  }

  /**
   * Returns the number of all the available (free) seats. The returned
   * information is consistent with the latest booking/cancellation performed.
   *
   * @return the number of each of the available seats
   * @throws DataAccessException if an unrecoverable error occurs
   * @throws java.sql.SQLException
   */
  public ArrayList<Integer> getAvailableSeats() throws DataAccessException, SQLException {
      
    String getReservedSeatsQuery = "SELECT SEAT FROM BOOKINGS WHERE CUSTOMER IS NULL;";
    ArrayList <Integer> seatsList = new ArrayList <> ();
    
    try{
        ps = conn.prepareStatement(getReservedSeatsQuery);
        rs = ps.executeQuery();
        
        while(rs.next()){
            seatsList.add(rs.getInt(1));
        }
        
        rs.close();
        ps.close();
        
    }catch(SQLException e){
        System.out.println("Error during statement preparation.");
    }
    
    if(seatsList.size()>0)
        return seatsList;
    else
        return null;
  }

  /**
   * Returns the booking info corresponding to the last order (booking or
   * cancellation) peformed by the specified customer. The returned information
   * is consistent with the latest booking/cancellation performed by the
   * specified customer, or performed by all customers if no customer is
   * specified.
   *
   * @param customer the customer for whom the booking info must be returned;
   * <code>null</code> if all information must be returned
   * @return the booking information corresponding to the specified customer, if
   * any; the booking information aggregated over all customer if no customer is
   * specified.
   * @throws DataAccessException if an unrecoverable error occurs
   */
  
  public BookingInfo getBookingInfo(String customer) throws DataAccessException, SQLException {
      String client = null;
      int amount = 0;
      Date date_order = null;
      try {
          
        String getBookingQuery=null;
        
        if(customer==null){
           getBookingQuery = "SELECT * FROM ORDERS ORDER BY ORDERS.ODATE DESC LIMIT 1";
           ps = this.conn.prepareStatement(getBookingQuery);
        }else{     
           getBookingQuery = "SELECT * FROM ORDERS WHERE CUSTOMER=? ORDER BY ORDERS.ODATE DESC LIMIT 1";
           ps = this.conn.prepareStatement(getBookingQuery);
           ps.setString(1,customer);
        }
        
        rs = ps.executeQuery();
        
        while(rs.next()){
            client = rs.getString(1);
            amount = rs.getInt(2);
            date_order = rs.getDate(3);
        }
        
        if(rs!=null)    rs.close(); 
        if(ps!=null)    ps.close();

        
        ArrayList <Integer> seatsList = new ArrayList <> ();
        String getTable = "SELECT SEAT FROM BOOKINGS WHERE CUSTOMER=?";
        ps = this.conn.prepareStatement(getTable);
        ps.setString(1,client);
        rs = ps.executeQuery();
        
        while(rs.next()){
            seatsList.add(rs.getInt(1));
        }
        
        if(rs!=null)    rs.close(); 
        if(ps!=null)    ps.close();

        BookingInfo booking = new BookingInfo(customer,amount,date_order,seatsList);
        return booking;
          
      }
      catch(SQLException e){
        System.out.println("Error during statement preparation.");
    }
      return null;
  }
}
