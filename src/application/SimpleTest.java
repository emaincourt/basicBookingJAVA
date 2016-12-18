package application;

import java.util.ArrayList;
import java.util.List;

import model.DataAccess;

/**
 * A simple test program for {@link DataAcces}.
 *
 * @author Jean-Michel Busca
 *
 */
public class SimpleTest {

  //
  // CONSTANTS
  //
  private static final int MAX_SEATS = 10;  // correlated with DB population
  private static final int MAX_CUSTOMERS = 5;

  //
  // CLASS FIELDS
  //
  private static int testTotal = 0;
  private static int testOK = 0;

  //
  // HELPER CLASSES
  //
  /**
   * Emulates a user performing booking operations. These operations are defined in
   * the {@link #run()} method.
   * <p>
   * This class is used to perform multi-user tests. See the
   * {@link SimpleTest#main(String[])} method.
   *
   * @author Jean-Michel Busca
   *
   */
  static class UserEmulator extends Thread {

    private final DataAccess store;
    private final String user;

    /**
     * Creates a new user emulator with the specified name, using the specified
     * data store manager.
     * <p>
     * Note: the data store manager must be dedicated to the user (no two users
     * share the same data store manager object).
     *
     * @param store
     *          the data access object to use
     * @param user
     *          the name of the user running the test
     */
    public UserEmulator(DataAccess store, String user) {
      this.store = store;
      this.user = user;
    }

    @Override
    public String toString() {
      return user + "[" + store + "]";
    }

    @Override
    public void run() {
      System.out.println(this + ": starting");

      // TODO complete the test

      System.out.println(this + ": exiting");
    }

  }

  //
  // HELPER METHODS
  //
  /**
   * Checks whether the specified test was successful and updates the fields
   * <code>testTotal</code> and <code>testOK</code> accordingly.
   *
   * @param test
   *          the name of the test
   * @param ok
   *          <code>true</code> if the test was sucessful and <code>false</code>
   *          otherwise
   */
  private synchronized static void check(String test, boolean ok) {
    testTotal += 1;
    System.out.print(test + ": ");
    if (ok) {
      testOK += 1;
      System.out.println("ok");
    } else {
      System.out.println("FAILED");
    }
  }

  /**
   * Runs a single-user test suite on the specified data store manager, on
   * behalf of the specified user.
   *
   * @param store
   *          the manager to test
   * @param user
   *          the name of the user running the test
   * @throws Exception
   *           if anything goes wrong
   */
  private static void singleUserTests(DataAccess store, String user)
          throws Exception {

    // NOTE: the tests below throw an NullPointerException because the methods
    // are not implemented yet
    check("initial state", store.getAvailableSeats().size() == MAX_SEATS);
    
    check("simple booking", store.book(user, 0, MAX_SEATS,false).getSeats().size() == MAX_SEATS);

    //check("simple cancellation", store.cancel(user, 0, MAX_SEATS).getSeats().size() == 0);
    
    // TODO complete the test
  }
  

  //
  // MAIN
  //
  /**
   * Runs the simple test program.
   *
   * @param args
   *          url login password
   *          <p>
   *          to be specified in Eclipse:<br>
   *          Run/Run Configurations.../Arguments/Program arguments
   */
  public static void main(String[] args) {

    // check parameters
    if (args.length != 3) {
      System.err.println("usage: SimpleTest <url> <login> <password>");
      System.exit(1);
    }

    DataAccess store = null;
    List<DataAccess> managers = new ArrayList<DataAccess>();
    try {

      // create the data store manager
      store = new DataAccess(args[0], args[1], args[2]);

      // create and populate the database

      // execute single-user tests
      System.out.println("Running single-user tests...");
      singleUserTests(store, "single user");

      // execute multi-users tests
      System.out.println("Running multi-users tests...");
      List<UserEmulator> emulators = new ArrayList<UserEmulator>();
      for (int i = 0; i < MAX_CUSTOMERS; i++) {
        DataAccess manager2 = new DataAccess(args[0], args[1],
                args[2]);
        managers.add(manager2);
        UserEmulator emulator = new UserEmulator(manager2, "user#" + i);
        emulators.add(emulator);
        emulator.start();
      }

      // wait for the test to complete
      for (UserEmulator e : emulators) {
        e.join();
      }

      // you may add some tests here:
      // TODO

    } catch (Exception e) {

      System.err.println("test aborted: " + e);
      e.printStackTrace();

    } finally {

      if (store != null) {
        try {
          store.close();
        } catch (Exception e) {
          System.err.println("unexpected exception: " + e);
        }
      }

      if (managers != null) {
        for (DataAccess m : managers) {
          try {
            m.close();
          } catch (Exception e) {
            System.err.println("unexpected exception: " + e);
          }
        }
      }

    }

    // print test results
    if (testTotal == 0) {
      System.out.println("no test performed");
    } else {
      String r = "test results: ";
      r += "total=" + testTotal;
      r += ", ok=" + testOK + " (" + ((testOK * 100) / testTotal) + "%)";
      System.out.println(r);
    }

  }
}
