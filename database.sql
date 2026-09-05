-- =====================================================================
--  SmileCare Dental Clinic - Appointment Management System
--  MySQL 8 schema and starter data
--
--  Run from the WAMP MySQL console:
--      mysql -u root -p < database.sql
-- =====================================================================

DROP DATABASE IF EXISTS smilecare;
CREATE DATABASE smilecare CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smilecare;

-- ---------------------------------------------------------------------
-- staff accounts
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(40)  NOT NULL UNIQUE,
    password    CHAR(64)     NOT NULL,          -- SHA-256 hex, never plain text
    role        ENUM('admin','receptionist','dentist') NOT NULL DEFAULT 'receptionist',
    full_name   VARCHAR(80)  NOT NULL,
    active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- patients
-- ---------------------------------------------------------------------
CREATE TABLE patients (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    address     VARCHAR(160) NOT NULL,
    contact_no  VARCHAR(15)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_patient_contact (contact_no)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- dentists and treatments
-- ---------------------------------------------------------------------
CREATE TABLE dentists (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(80)   NOT NULL,
    qualification    VARCHAR(80),
    speciality       VARCHAR(80),
    consultation_fee DECIMAL(10,2) NOT NULL,
    active           TINYINT(1)    NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE treatments (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    treatment_type VARCHAR(80)   NOT NULL,
    base_cost      DECIMAL(10,2) NOT NULL,
    duration_mins  INT           NOT NULL DEFAULT 30,
    active         TINYINT(1)    NOT NULL DEFAULT 1
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- appointments
-- ---------------------------------------------------------------------
CREATE TABLE appointments (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    appointment_no  VARCHAR(12) NOT NULL UNIQUE,          -- APT-1042
    patient_id      INT         NOT NULL,
    dentist_id      INT         NOT NULL,
    treatment_id    INT         NOT NULL,
    appt_date       DATE        NOT NULL,
    appt_time       TIME        NOT NULL,
    status          ENUM('Pending','Confirmed','Completed','Cancelled')
                    NOT NULL DEFAULT 'Pending',
    created_by      INT,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_appt_patient   FOREIGN KEY (patient_id)   REFERENCES patients(id),
    CONSTRAINT fk_appt_dentist   FOREIGN KEY (dentist_id)   REFERENCES dentists(id),
    CONSTRAINT fk_appt_treatment FOREIGN KEY (treatment_id) REFERENCES treatments(id),
    CONSTRAINT fk_appt_user      FOREIGN KEY (created_by)   REFERENCES users(id),

    INDEX idx_appt_day (appt_date, appt_time)
) ENGINE=InnoDB;

-- A dentist cannot be in two places at once. This is NOT a unique index,
-- because a cancelled appointment must leave its slot free for a new booking.
-- The DAO runs this check before every insert:
--   SELECT COUNT(*) FROM appointments
--   WHERE dentist_id = ? AND appt_date = ? AND appt_time = ? AND status <> 'Cancelled';
CREATE INDEX idx_dentist_slot
    ON appointments (dentist_id, appt_date, appt_time);

-- ---------------------------------------------------------------------
-- bills
-- ---------------------------------------------------------------------
CREATE TABLE bills (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    bill_no          VARCHAR(12)   NOT NULL UNIQUE,       -- BIL-7101
    appointment_id   INT           NOT NULL UNIQUE,
    treatment_cost   DECIMAL(10,2) NOT NULL,
    consultation_fee DECIMAL(10,2) NOT NULL,
    total            DECIMAL(10,2)
                     GENERATED ALWAYS AS (treatment_cost + consultation_fee) STORED,
    pricing_strategy VARCHAR(40)   NOT NULL DEFAULT 'standard',
    issued_by        INT,
    issued_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bill_appt FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_bill_user FOREIGN KEY (issued_by)      REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- audit log
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT,
    action      VARCHAR(40)  NOT NULL,      -- CREATE_APPOINTMENT, ISSUE_BILL...
    entity      VARCHAR(40)  NOT NULL,      -- appointments, bills
    entity_ref  VARCHAR(20),                -- APT-1042
    details     VARCHAR(255),
    logged_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- notifications - every confirmation, reminder, cancellation notice and
-- receipt e-mail the system has sent, for the Reminders screen. Written
-- by EmailListener and by the manual "Send reminder" button.
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    appointment_no VARCHAR(12) NOT NULL,
    recipient      VARCHAR(120),                 -- e-mail or phone; NULL means the clinic itself
    type           ENUM('Confirmation','Reminder','Cancellation','Receipt') NOT NULL,
    subject        VARCHAR(160) NOT NULL,
    message        VARCHAR(500) NOT NULL,
    sent_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_appt (appointment_no)
) ENGINE=InnoDB;


-- =====================================================================
--  starter data - matches what the front end shows in demo mode
-- =====================================================================

-- Passwords are stored as SHA-256. In Java, hash the submitted password the
-- same way and compare the hex strings:
--     MessageDigest.getInstance("SHA-256").digest(pw.getBytes(UTF_8))
INSERT INTO users (username, password, role, full_name) VALUES
  ('admin',     SHA2('admin123', 256),  'admin',        'Chathura Bandara'),
  ('reception', SHA2('clinic123', 256), 'receptionist', 'Ishara Madushani');

INSERT INTO dentists (name, qualification, speciality, consultation_fee) VALUES
  ('Dr. Anushka Fernando',      'BDS (Colombo)',        'Restorative dentistry', 2000.00),
  ('Dr. Ruwan Jayasuriya',      'BDS, MS',              'Oral surgery',          2500.00),
  ('Dr. Sanduni Wickramasinghe','BDS, MSc',             'Orthodontics',          2500.00);

INSERT INTO treatments (treatment_type, base_cost, duration_mins) VALUES
  ('Routine check-up',       1500.00, 20),
  ('Scaling and polishing',  4500.00, 45),
  ('Tooth filling',          3500.00, 40),
  ('Root canal treatment',  18000.00, 60),
  ('Tooth extraction',       5000.00, 30),
  ('Braces consultation',   95000.00, 45);

INSERT INTO patients (name, address, contact_no) VALUES
  ('Nimal Perera',      '45/2, Temple Road, Nugegoda',  '0771234567'),
  ('Kamani Silva',      '12, Flower Road, Colombo 07',  '0712223344'),
  ('Rashmi Alwis',      '8, Lake Drive, Rajagiriya',    '0765556677'),
  ('Sunil Wijesinghe',  '221B, Galle Road, Dehiwala',   '0778889900');

INSERT INTO appointments
  (appointment_no, patient_id, dentist_id, treatment_id, appt_date, appt_time, status, created_by)
VALUES
  ('APT-1042', 1, 1, 2, CURDATE(), '10:30:00', 'Confirmed', 2),
  ('APT-1043', 2, 1, 3, CURDATE(), '11:15:00', 'Pending',   2),
  ('APT-1044', 3, 2, 4, CURDATE(), '14:00:00', 'Completed', 2),
  ('APT-1045', 4, 3, 1, CURDATE(), '15:30:00', 'Cancelled', 2);

INSERT INTO bills (bill_no, appointment_id, treatment_cost, consultation_fee, issued_by)
SELECT 'BIL-7101', a.id, t.base_cost, d.consultation_fee, 2
FROM appointments a
JOIN treatments t ON t.id = a.treatment_id
JOIN dentists  d ON d.id = a.dentist_id
WHERE a.appointment_no = 'APT-1044';


-- =====================================================================
--  queries the DAOs need
-- =====================================================================

-- the diary, already joined the way the front end expects it
CREATE OR REPLACE VIEW v_appointments AS
SELECT a.appointment_no                AS appointmentNo,
       p.name                          AS patientName,
       p.address                       AS address,
       p.contact_no                    AS contactNo,
       d.id                            AS dentistId,
       d.name                          AS dentistName,
       d.consultation_fee              AS consultationFee,
       t.id                            AS treatmentId,
       t.treatment_type                AS treatmentType,
       t.base_cost                     AS treatmentCost,
       a.appt_date                     AS `date`,
       a.appt_time                     AS `time`,
       a.status                        AS status
FROM appointments a
JOIN patients   p ON p.id = a.patient_id
JOIN dentists   d ON d.id = a.dentist_id
JOIN treatments t ON t.id = a.treatment_id;

-- next appointment number:
--   SELECT CONCAT('APT-', IFNULL(MAX(CAST(SUBSTRING(appointment_no, 5) AS UNSIGNED)), 1041) + 1)
--   FROM appointments;

-- daily report:
--   SELECT * FROM v_appointments WHERE `date` = CURDATE() ORDER BY `time`;

-- revenue report:
--   SELECT DATE(issued_at) AS day, COUNT(*) AS bills, SUM(total) AS revenue
--   FROM bills GROUP BY day ORDER BY day DESC;

-- most common treatments:
--   SELECT t.treatment_type, COUNT(*) AS times_booked
--   FROM appointments a JOIN treatments t ON t.id = a.treatment_id
--   WHERE a.status <> 'Cancelled'
--   GROUP BY t.treatment_type ORDER BY times_booked DESC;
