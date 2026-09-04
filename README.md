# SmileCare Dental Clinic — Appointment Management System

A three tier appointment system for a dental clinic: a plain HTML/CSS/JavaScript
front end, a Java backend built on the JDK's own `com.sun.net.httpserver`, and
MySQL underneath. No web framework, no build tool — `javac` and a jar.

```
Presentation   index.html and the staff pages  +  com.smilecare.http
Business       com.smilecare.service
Data           com.smilecare.dao  ->  com.smilecare.db  ->  MySQL
```

## Running it

**1. Create the database** (MySQL must be started in WAMP)

```
cd "C:\wamp64\www\apn madhava"
"C:\wamp64\bin\mysql\mysql9.1.0\bin\mysql.exe" -u root < database.sql
```

**2. Build and start the server**

```
cd backend
compile.bat
run.bat
```

`compile.bat` needs a JDK 17 or newer on the PATH. If the port is busy,
`run.bat 8091` starts it somewhere else.

**3. Open it**

| | |
|---|---|
| Clinic website | http://localhost:8090/ |
| Staff login | http://localhost:8090/login.html |

| Username | Password | Role |

# ap-new-
|---|---|---|
| `admin` | `admin123` | administrator |
| `reception` | `clinic123` | receptionist |

The server hosts the pages as well as the API, so everything runs on one port
and the browser never has to deal with CORS. Opening the pages through WAMP on
`http://localhost/apn%20madhava/` also works — the front end then calls
`localhost:8090` and the server sends the CORS headers for it.

> Port 8080 is already used by WAMP's Apache on this machine, which is why the
> project uses **8090**. To change it, edit `server.port` in
> `backend/config.properties` **and** the port in `js/app.js`.

### Working without the backend

If the Java server is not running, the pages fall back to sample data kept in
the browser and show a yellow **Demo mode** notice. Every screen still works,
so the interface can be demonstrated on a machine with no database. Nothing is
written to MySQL in that mode.

## What it does

| Feature | Screen |
|---|---|
| Staff login and safe exit | `login.html`, sidebar |
| Register an appointment | `appointment.html` |
| Display appointment details | `search.html`, plus the lookup on the home page |
| Calculate and print the bill | `billing.html` |
| Help and instructions | `help.html` |
| Reschedule, confirm, cancel | `search.html` |
| Audit log of who did what | `audit_log` table, written automatically |
| Confirmation e-mail to the patient | `EmailListener`, logged to the console |

Appointment numbers (`APT-1042`) and receipt numbers (`BIL-7101`) are generated
by the server, never typed by staff. One dentist cannot be booked twice in the
same slot; a cancelled appointment frees its slot again.

## Design patterns used

| Pattern | Where | Why it is there |
|---|---|---|
| **Singleton** | `AppConfig`, `DbConnection` | one set of settings, one place that makes connections |
| **Factory** | `DaoFactory` | services ask for a DAO instead of calling `new` |
| **DAO** | `dao` package | no SQL anywhere else in the program |
| **DTO** | `model` package | immutable records carry data between the layers |
| **Strategy** | `PricingStrategy`, `StandardPricing`, `PromotionalPricing` | the charging rule can change without touching billing |
| **Observer** | `AppointmentListener`, `AuditListener`, `EmailListener` | booking announces what happened; listeners react |
| **Template method** | `ApiHandler` | every endpoint shares CORS, error handling and auth |
| **MVC** | pages / handlers / services | the browser draws, the server decides |

To see Strategy switch, put this in `backend/config.properties` and restart:

```
pricing.strategy=promotional
pricing.discount=15
```

## Layout

```
index.html              clinic website, public
login.html              staff sign-in
appointment.html        register an appointment
search.html             find one, and the day diary
billing.html            charges, receipt, print
help.html               instructions for the front desk
css/styles.css          website + shared design tokens
css/app.css             the staff application shell
js/app.js               API wrapper, session, toasts, demo fallback
js/*.js                 one file per screen
database.sql            schema, seed data and the report queries
API.md                  the contract between the two halves
backend/
  config.properties     database, port, pricing rule
  compile.bat, run.bat
  lib/                  MySQL Connector/J and org.json
  src/com/smilecare/
    Main.java           wires everything together and starts the server
    config/ db/ dao/ model/ service/ http/
```

## Things worth knowing

- Passwords are stored as SHA-256 hex, written by MySQL's `SHA2(pw, 256)` in
  `database.sql` and checked the same way in `AuthService`. The plain password
  is never stored or logged.
- Sessions live in memory, so restarting the server signs everybody out.
- Every SQL value is bound as a parameter, so the search box cannot be used for
  SQL injection.
- Issuing a bill writes the receipt and completes the appointment inside one
  transaction, and asking twice returns the receipt that already exists rather
  than writing a second one.
- `StaticHandler` normalises the path and checks it is still inside the web
  root, so `../` cannot reach files outside the project.
