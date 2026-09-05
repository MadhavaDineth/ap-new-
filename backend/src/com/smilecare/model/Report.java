package com.smilecare.model;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The DTOs the reports screen is built from.
 *
 * They live together in one file because none of them means anything on its
 * own - each is one shape of answer to "how is the clinic doing". Keeping them
 * nested here says that plainly, and stops six near-identical files appearing
 * in the model package.
 */
public final class Report {

    private Report() { }

    /** The four figures along the top of the reports screen. */
    public record Summary(int todayCount,
                          int pendingCount,
                          BigDecimal todayRevenue,
                          BigDecimal monthRevenue,
                          int monthBills,
                          int patientCount) {

        public JSONObject toJson() {
            return new JSONObject()
                .put("todayCount", todayCount)
                .put("pendingCount", pendingCount)
                .put("todayRevenue", todayRevenue)
                .put("monthRevenue", monthRevenue)
                .put("monthBills", monthBills)
                .put("patientCount", patientCount);
        }
    }

    /** One day of the revenue trend. */
    public record DayRevenue(LocalDate day, int bills, BigDecimal revenue) {

        public JSONObject toJson() {
            return new JSONObject()
                .put("day", day.toString())
                .put("bills", bills)
                .put("revenue", revenue);
        }
    }

    /** One day of the appointment count. */
    public record DayCount(LocalDate day, int total, int cancelled) {

        public JSONObject toJson() {
            return new JSONObject()
                .put("day", day.toString())
                .put("total", total)
                .put("cancelled", cancelled);
        }
    }

    /** How often a treatment is booked, and what it is worth. */
    public record TreatmentCount(String treatment, int bookings, BigDecimal value) {

        public JSONObject toJson() {
            return new JSONObject()
                .put("treatment", treatment)
                .put("bookings", bookings)
                .put("value", value);
        }
    }

    /** How busy one dentist is. */
    public record Workload(String dentist, int appointments, int completed) {

        public JSONObject toJson() {
            return new JSONObject()
                .put("dentist", dentist)
                .put("appointments", appointments)
                .put("completed", completed);
        }
    }

    /** How much a patient has visited, and paid, in total. */
    public record PatientCount(String patientName, int visits, BigDecimal spent) {

        public JSONObject toJson() {
            return new JSONObject()
                .put("patientName", patientName)
                .put("visits", visits)
                .put("spent", spent);
        }
    }

    /** How the diary splits across the four statuses. */
    public record StatusMix(int pending, int confirmed, int completed, int cancelled) {

        public int total() {
            return pending + confirmed + completed + cancelled;
        }

        public JSONObject toJson() {
            return new JSONObject()
                .put("Pending", pending)
                .put("Confirmed", confirmed)
                .put("Completed", completed)
                .put("Cancelled", cancelled)
                .put("total", total());
        }
    }
}
