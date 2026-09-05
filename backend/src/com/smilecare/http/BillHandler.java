package com.smilecare.http;

import com.smilecare.model.Bill;
import com.smilecare.service.AppException;
import com.smilecare.service.AuthService;
import com.smilecare.service.BillingService;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.IOException;

/**
 * GET  /api/bills/{appointmentNo}   a receipt that has already been issued
 * POST /api/bills                   issue one
 *
 * Both need a signed-in member of staff: money is not public.
 */
public class BillHandler extends ApiHandler {

    public static final String PATH = "/api/bills";

    private final BillingService billing;

    public BillHandler(AuthService auth, BillingService billing) {
        super(auth);
        this.billing = billing;
    }

    @Override
    protected void route(HttpExchange ex) throws IOException {
        String number = Http.tail(ex, PATH);
        String method = ex.getRequestMethod().toUpperCase();

        if (number.isEmpty() && method.equals("POST")) {
            issue(ex);
        } else if (!number.isEmpty() && method.equals("GET")) {
            requireUser(ex);
            Http.json(ex, 200, billing.require(number.toUpperCase()).toJson());
        } else {
            methodNotAllowed(ex);
        }
    }

    private void issue(HttpExchange ex) throws IOException {
        Integer userId = requireUser(ex).id();
        JSONObject in = Http.jsonBody(ex);

        String appointmentNo = in.optString("appointmentNo", "").trim();
        if (appointmentNo.isEmpty()) {
            throw AppException.badRequest("Which appointment is this bill for?");
        }
        // null/blank means "use whatever config.properties has configured";
        // the billing screen normally does send an explicit choice
        String pricingStrategy = in.has("pricingStrategy") ? in.optString("pricingStrategy") : null;

        Bill bill = billing.issue(appointmentNo.toUpperCase(), pricingStrategy, userId);
        Http.json(ex, 201, bill.toJson());
    }
}
