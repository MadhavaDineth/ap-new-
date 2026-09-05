package com.smilecare;

import com.smilecare.config.AppConfig;
import com.smilecare.db.DbConnection;
import com.smilecare.http.AdminHandler;
import com.smilecare.http.AppointmentHandler;
import com.smilecare.http.AuthHandler;
import com.smilecare.http.BillHandler;
import com.smilecare.http.NotificationHandler;
import com.smilecare.http.PatientHandler;
import com.smilecare.http.ReferenceHandler;
import com.smilecare.http.ReportHandler;
import com.smilecare.http.StaticHandler;
import com.smilecare.service.AppointmentService;
import com.smilecare.service.AuditListener;
import com.smilecare.service.AuthService;
import com.smilecare.service.AdminService;
import com.smilecare.service.BillingService;
import com.smilecare.service.NotificationService;
import com.smilecare.service.PatientService;
import com.smilecare.service.ReportService;
import com.smilecare.service.EmailListener;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SmileCare Dental Clinic - Appointment Management System
 *
 *   Presentation : index.html and the other pages, plus the handlers here
 *   Business     : the service package
 *   Data         : the dao package, reached through DaoFactory
 *
 * Start with backend\run.bat, or from an IDE with this class as the main class.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        AppConfig config = AppConfig.get();

        // fail now, with a clear message, rather than on the first request
        try {
            DbConnection.get().verify();
        } catch (SQLException e) {
            System.err.println();
            System.err.println("Could not reach MySQL: " + e.getMessage());
            System.err.println("  * is MySQL started in WAMP?");
            System.err.println("  * has database.sql been imported?");
            System.err.println("  * are db.user and db.password right in config.properties?");
            System.err.println();
            System.exit(1);
            return;
        }

        // ---- business layer, wired once ----------------------------------
        AuthService auth = new AuthService();
        AppointmentService appointments = new AppointmentService();
        appointments.addListener(new AuditListener());   // observer 1: audit trail
        appointments.addListener(new EmailListener());   // observer 2: patient e-mail
        BillingService billing = new BillingService(appointments);
        ReportService reports = new ReportService();
        AdminService admin = new AdminService();
        PatientService patients = new PatientService();
        NotificationService notifications = new NotificationService(appointments);

        // ---- http layer ---------------------------------------------------
        // config.properties sets the port; a command line argument wins over
        // it, which is handy when something else has grabbed the usual one.
        int port = config.port();
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Ignoring \"" + args[0] + "\": that is not a port number.");
            }
        }

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (java.net.BindException e) {
            System.err.println();
            System.err.println("Port " + port + " is already being used by another program.");
            System.err.println("  * change server.port in backend/config.properties, or");
            System.err.println("  * start with a different port:  run.bat 8091");
            System.err.println("Remember to change the port in js/app.js to match.");
            System.err.println();
            System.exit(1);
            return;
        }

        server.createContext(AuthHandler.PATH, new AuthHandler(auth));
        server.createContext("/api/dentists", new ReferenceHandler(auth, "dentists"));
        server.createContext("/api/treatments", new ReferenceHandler(auth, "treatments"));
        server.createContext(AppointmentHandler.PATH, new AppointmentHandler(auth, appointments));
        server.createContext(BillHandler.PATH, new BillHandler(auth, billing));
        server.createContext(ReportHandler.PATH, new ReportHandler(auth, reports));
        server.createContext(AdminHandler.PATH, new AdminHandler(auth, admin));
        server.createContext(PatientHandler.PATH, new PatientHandler(auth, patients));
        server.createContext(NotificationHandler.PATH, new NotificationHandler(auth, notifications));
        server.createContext("/", new StaticHandler(config.webRoot()));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        server.setExecutor(pool);
        server.start();

        System.out.println();
        System.out.println("  SmileCare server is running");
        System.out.println("  Front desk : http://localhost:" + port + "/login.html");
        System.out.println("  Website    : http://localhost:" + port + "/");
        System.out.println("  Press Ctrl+C to stop.");
        System.out.println();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nStopping the server...");
            server.stop(1);
            pool.shutdown();
        }));
    }
}
