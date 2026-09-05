package com.smilecare.db;

import com.smilecare.dao.AppointmentDao;
import com.smilecare.dao.AuditDao;
import com.smilecare.dao.BillDao;
import com.smilecare.dao.DentistDao;
import com.smilecare.dao.NotificationDao;
import com.smilecare.dao.PatientDao;
import com.smilecare.dao.ReportDao;
import com.smilecare.dao.TreatmentDao;
import com.smilecare.dao.UserDao;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Design pattern: FACTORY.
 *
 * The services ask this class for a DAO instead of calling `new` themselves.
 * Nothing outside this file needs to know which concrete class is used, so a
 * DAO can be swapped for a different implementation (a MySQL one, an in-memory
 * one for testing) by editing a single line here.
 *
 * DAOs hold no request state, so one shared instance of each is enough.
 */
public final class DaoFactory {

    private static final Map<Class<?>, Object> CACHE = new ConcurrentHashMap<>();

    private DaoFactory() { }

    @SuppressWarnings("unchecked")
    private static <T> T obtain(Class<T> type, Supplier<T> maker) {
        return (T) CACHE.computeIfAbsent(type, k -> maker.get());
    }

    public static UserDao        users()        { return obtain(UserDao.class, UserDao::new); }
    public static PatientDao     patients()     { return obtain(PatientDao.class, PatientDao::new); }
    public static DentistDao     dentists()     { return obtain(DentistDao.class, DentistDao::new); }
    public static TreatmentDao   treatments()   { return obtain(TreatmentDao.class, TreatmentDao::new); }
    public static AppointmentDao appointments() { return obtain(AppointmentDao.class, AppointmentDao::new); }
    public static BillDao        bills()        { return obtain(BillDao.class, BillDao::new); }
    public static AuditDao       audit()        { return obtain(AuditDao.class, AuditDao::new); }
    public static ReportDao      reports()      { return obtain(ReportDao.class, ReportDao::new); }
    public static NotificationDao notifications() { return obtain(NotificationDao.class, NotificationDao::new); }
}
