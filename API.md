# API contract

The front end talks to the Java `HttpServer` only through these endpoints.
`js/app.js` sends every request; if the server does not answer, the pages fall
back to sample data held in the browser and show a yellow **Demo mode** notice.

- Base URL: `http://localhost:8090`
- Everything is JSON, `Content-Type: application/json`
- Errors return the right status code and `{ "message": "..." }`, which the
  front end shows to the user as written

## CORS

The server hosts the pages itself, so the simplest setup has no CORS at all:
open **http://localhost:8090/** and the browser sees one origin.

If the pages are opened through WAMP on port 80 instead, the two ports are
different origins. `ApiHandler` already sends the headers for that on every
response, and answers the browser pre-flight:

```java
h.set("Access-Control-Allow-Origin", AppConfig.get().corsOrigin());
h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
h.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, OPTIONS");
```

The allowed origin comes from `cors.origin` in `backend/config.properties`.

## Who has to be signed in

| Endpoint | Sign-in |
|---|---|
| `GET /api/dentists`, `GET /api/treatments` | no, the website shows them |
| `GET /api/appointments/{no}` | no, a patient checks their own booking |
| `/api/admin/...` | yes, and the account must have the `admin` role, else `403` |
| everything else | yes, `Authorization: Bearer <token>` |

## Endpoints

| Method | Path | Purpose | Used by |
|---|---|---|---|
| POST | `/api/auth/login` | Sign a staff user in | login.html |
| POST | `/api/auth/logout` | End the session | sidebar, Exit |
| GET | `/api/dentists` | Dentist list with fees | appointment.html |
| GET | `/api/treatments` | Treatment list with costs | appointment.html |
| GET | `/api/appointments` | Diary, filterable | search.html |
| POST | `/api/appointments` | Register a new appointment | appointment.html |
| GET | `/api/appointments/{no}` | One appointment | index, search, billing |
| PUT | `/api/appointments/{no}` | Change the status | search.html |
| GET | `/api/bills/{no}` | Receipt already issued | billing.html |
| POST | `/api/bills` | Issue the receipt | billing.html |
| GET | `/api/patients` | Patient list, `?q=` to search | patients.html |
| GET | `/api/patients/{id}` | One patient | patients.html |
| GET | `/api/patients/{id}/history` | Every visit that patient has had | patients.html |
| POST | `/api/patients` | Add a patient | patients.html |
| PUT | `/api/patients/{id}` | Edit a patient | patients.html |
| DELETE | `/api/patients/{id}` | Remove a patient with no visits | patients.html |
| GET | `/api/notifications` | Reminders log, `?limit=` | reminders.html |
| POST | `/api/notifications/remind` | Send a manual reminder | search.html |
| GET | `/api/reports/summary` | Today, pending, month revenue, patients | dashboard, reports |
| GET | `/api/reports/revenue` | `?days=` or `?from=&to=` | dashboard, reports |
| GET | `/api/reports/appointments` | Per day, `?days=` | dashboard.html |
| GET | `/api/reports/treatments` | Most booked, `?limit=` | dashboard, reports |
| GET | `/api/reports/patients` | Top patients by spend, `?limit=` | reports.html |
| GET | `/api/reports/status` | The status breakdown | dashboard.html |
| GET | `/api/reports/workload` | Appointments per dentist | dashboard, reports |
| GET/POST/PUT | `/api/admin/dentists` | Manage dentists | admin.html |
| GET/POST/PUT | `/api/admin/treatments` | Manage the price list | admin.html |
| GET/POST/PUT | `/api/admin/users` | Manage staff accounts | admin.html |
| GET | `/api/admin/audit` | Audit trail, `?limit=` | admin.html |

### POST /api/auth/login

```json
{ "username": "reception", "password": "clinic123" }
```

```json
{ "username": "reception", "fullName": "Ishara Madushani",
  "role": "receptionist", "token": "..." }
```

`401` if the credentials are wrong. `token` is optional; when present the front
end returns it on later requests as `Authorization: Bearer <token>`.

### GET /api/dentists / GET /api/treatments

```json
[ { "id": 1, "name": "Dr. Anushka Fernando", "consultationFee": 2000 } ]
[ { "id": 2, "treatmentType": "Scaling and polishing", "baseCost": 4500 } ]
```

### GET /api/appointments

Optional query parameters, combined with AND: `date=2026-09-10`,
`status=Pending`, `q=` (matches patient name, appointment number or phone).
Returns an array of the appointment object below, sorted by date and time.

### POST /api/appointments

```json
{ "patientName": "Nimal Perera", "contactNo": "0771234567",
  "address": "45/2, Temple Road, Nugegoda",
  "dentistId": 1, "treatmentId": 2, "date": "2026-09-10", "time": "10:30" }
```

The handler looks the patient up by contact number and inserts a `patients` row
only if there is no match, generates the next `APT-nnnn`, and saves with status
`Pending`. Respond with the full appointment object:

```json
{ "appointmentNo": "APT-1042", "patientName": "Nimal Perera",
  "contactNo": "0771234567", "address": "45/2, Temple Road, Nugegoda",
  "dentistId": 1, "dentistName": "Dr. Anushka Fernando", "consultationFee": 2000,
  "treatmentId": 2, "treatmentType": "Scaling and polishing", "treatmentCost": 4500,
  "date": "2026-09-10", "time": "10:30", "status": "Pending" }
```

`409` if that dentist already has a non-cancelled appointment at the same date
and time. The message is shown under the time field, so make it readable.

### PUT /api/appointments/{no}

```json
{ "status": "Confirmed" }
```

Valid values: `Pending`, `Confirmed`, `Completed`, `Cancelled`. Returns the
updated appointment object. `404` if the number does not exist.

### POST /api/bills

```json
{ "appointmentNo": "APT-1042", "pricingStrategy": "senior" }
```

`pricingStrategy` is optional and picks the rule the charge is worked out with.
Leaving it out falls back to `pricing.strategy` in `config.properties`.

| Key | Rule |
|---|---|
| `standard` | full treatment cost + full consultation fee |
| `senior` | 15% off the consultation fee |
| `insurance` | the patient pays 80% of both charges, the insurer is billed separately |
| `loyalty` | 10% off the treatment cost |

Anything else is a `400`. The amounts are always recalculated on the server, so
the browser cannot talk the clinic into a discount. Issuing a bill also sets the
appointment to `Completed`.

```json
{ "billNo": "BIL-7101", "appointmentNo": "APT-1042",
  "patientName": "Nimal Perera", "dentistName": "Dr. Anushka Fernando",
  "treatmentType": "Scaling and polishing",
  "treatmentCost": 4500, "consultationFee": 1500,
  "total": 6000, "pricingStrategy": "standard",
  "issuedAt": "2026-09-10T11:20:00" }
```

`404` if the appointment does not exist, `409` if it was cancelled.
`GET /api/bills/{no}` returns the same object, or `404` when nothing has been
issued yet — billing.html relies on that 404 to know it should show the preview.

### /api/patients

```json
{ "id": 1, "name": "Nimal Perera", "contactNo": "0771234567",
  "address": "45/2, Temple Road, Nugegoda" }
```

`POST` and `PUT` take the same three fields. The contact number must be 10
digits starting with 0, and it is what identifies a returning patient, so a
`POST` with a number already on file is a `409`.

`DELETE` answers `409` for a patient who has any appointment: their visits and
receipts must keep pointing at a real person. `GET /api/patients/{id}/history`
returns an array of appointment objects, newest first.

### /api/notifications

```json
{ "id": 12, "appointmentNo": "APT-1042", "recipient": "0771234567",
  "type": "Reminder", "subject": "Reminder: appointment APT-1042",
  "message": "Dear Nimal Perera, ...", "sentAt": "2026-09-10T09:00:00" }
```

`type` is `Confirmation`, `Reminder`, `Cancellation` or `Receipt`. The first,
third and fourth are written automatically by `EmailListener` as bookings,
cancellations and bills happen. `POST /api/notifications/remind` with
`{ "appointmentNo": "APT-1042" }` writes a `Reminder`; a completed or cancelled
visit is a `409`.

### /api/reports

`summary` returns `todayCount`, `pendingCount`, `todayRevenue`, `monthRevenue`,
`monthBills` and `patientCount`. `revenue` takes either `?days=n` (the last n
days) or `?from=&to=` (an explicit range, `400` if from is after to), and
returns `[{ "day": "2026-09-10", "bills": 3, "revenue": 18500 }]`. `patients`
returns `[{ "patientName": "...", "visits": 4, "spent": 24000 }]`.

### /api/admin

Dentists, treatments and staff accounts are created with `POST` and changed with
`PUT /{id}`. Nothing is deleted — `active: false` retires a dentist, withdraws a
treatment or suspends an account, and the booking screens simply stop offering
inactive rows. Suspending or demoting the last active administrator is a `409`.
A `password` on `PUT /api/admin/users/{id}` is optional; blank keeps the
existing one.

## Turning the demo data off

Nothing needs changing to go live: the browser fallback only runs when `fetch`
itself fails, so as soon as the Java server answers, the real database is in
use and the yellow notice disappears. To drop the fallback altogether for the
final submission, delete the `Demo` block and the `.catch` in `js/app.js`.
