package com.smilecare.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Design pattern: DAO (Data Access Object).
 *
 * Every class that touches the database implements this, so the rest of the
 * program never sees a Connection, a PreparedStatement or a ResultSet. If the
 * clinic ever moved off MySQL, only this package would change.
 *
 * Appointments and bills are looked up by their printed number rather than by
 * an integer id, so those DAOs add their own finders on top of this contract.
 */
public interface Dao<T> {

    List<T> findAll() throws SQLException;

    Optional<T> findById(int id) throws SQLException;
}
