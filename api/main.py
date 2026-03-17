import hashlib
import base64
from fastapi import FastAPI, HTTPException, Depends
from fastapi.responses import Response
from pydantic import BaseModel
import pyodbc
from db import get_connection
from models import (
    LoginRequest, RegisterRequest, UserResponse,
    CarCreate, CarUpdate,
    RefuelingCreate, RefuelingUpdate,
    MaintenanceCreate, MaintenanceUpdate,
)

app = FastAPI(title="MyCar API")


# ─── helpers ────────────────────────────────────────────────────────────────

def hash_password(password: str) -> str:
    digest = hashlib.sha256(password.encode()).digest()
    # Base64.DEFAULT в Android добавляет \n — добавляем для совместимости
    return base64.b64encode(digest).decode() + "\n"

def verify_password(plain: str, stored: str) -> bool:
    """Сравнивает пароль с хешем, игнорируя пробелы/переносы строк.
    Поддерживает хеши как с \n (Android Base64.DEFAULT), так и без."""
    candidate = hashlib.sha256(plain.encode()).digest()
    candidate_b64 = base64.b64encode(candidate).decode().strip()
    return candidate_b64 == stored.strip()

def row_to_dict(cursor, row):
    return {col[0]: val for col, val in zip(cursor.description, row)}

def parse_date(date_str: str) -> str:
    """Convert dd.MM.yyyy → yyyyMMdd for SQL CONVERT(..., 112)"""
    parts = date_str.strip().split(".")
    if len(parts) == 3:
        return f"{parts[2]}{parts[1]}{parts[0]}"
    return date_str  # already in another format, pass as-is


# ─── auth ────────────────────────────────────────────────────────────────────

@app.post("/auth/login", response_model=UserResponse)
def login(body: LoginRequest):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT user_id, full_name, username, email, password FROM users WHERE username = ?",
            body.username,
        )
        row = cur.fetchone()
    if not row:
        raise HTTPException(status_code=401, detail="Пользователь не найден")
    if not verify_password(body.password, row.password):
        raise HTTPException(status_code=401, detail="Неверный пароль")
    return UserResponse(user_id=row.user_id, full_name=row.full_name,
                        username=row.username, email=row.email)


@app.post("/auth/register", response_model=UserResponse)
def register(body: RegisterRequest):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT 1 FROM users WHERE username = ?", body.username)
        if cur.fetchone():
            raise HTTPException(status_code=409, detail="Логин уже занят")
        cur.execute("SELECT 1 FROM users WHERE email = ?", body.email)
        if cur.fetchone():
            raise HTTPException(status_code=409, detail="Email уже используется")

        hashed = hash_password(body.password)
        cur.execute(
            "INSERT INTO users (full_name, email, username, password) "
            "VALUES (?, ?, ?, ?)",
            body.full_name, body.email, body.username, hashed,
        )
        new_id = cur.execute("SELECT MAX(user_id) FROM users WHERE username = ?", body.username).fetchone()[0]
        conn.commit()
    return UserResponse(user_id=new_id, full_name=body.full_name,
                        username=body.username, email=body.email)


# ─── cars ────────────────────────────────────────────────────────────────────

@app.get("/users/{user_id}/cars")
def get_cars(user_id: int):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """SELECT c.car_id, cb.name AS brand, cm.name AS model, c.mileage
               FROM Cars c
               LEFT JOIN CarModels cm ON c.model_id = cm.model_id
               LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
               WHERE c.user_id = ?""",
            user_id,
        )
        rows = cur.fetchall()
    return [row_to_dict(cur, r) for r in rows]


@app.get("/users/{user_id}/cars/{car_id}/photo")
def get_car_photo(user_id: int, car_id: int):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT photo FROM Cars WHERE car_id = ? AND user_id = ?",
            car_id, user_id,
        )
        row = cur.fetchone()
    if not row or not row[0]:
        raise HTTPException(status_code=404, detail="Фото не найдено")
    return Response(content=bytes(row[0]), media_type="image/jpeg")


@app.post("/users/{user_id}/cars", status_code=201)
def add_car(user_id: int, body: CarCreate):
    with get_connection() as conn:
        cur = conn.cursor()

        brand_id = body.brand_id
        if not brand_id and body.brand_name:
            cur.execute("SELECT brand_id FROM CarBrands WHERE name = ?", body.brand_name)
            r = cur.fetchone()
            if r:
                brand_id = r[0]
            else:
                cur.execute("INSERT INTO CarBrands (name) VALUES (?)", body.brand_name)
                brand_id = cur.execute("SELECT MAX(brand_id) FROM CarBrands WHERE name = ?", body.brand_name).fetchone()[0]

        model_id = body.model_id
        if not model_id and body.model_name and brand_id:
            cur.execute("SELECT model_id FROM CarModels WHERE name = ? AND brand_id = ?", body.model_name, brand_id)
            r = cur.fetchone()
            if r:
                model_id = r[0]
            else:
                cur.execute("INSERT INTO CarModels (name, brand_id) VALUES (?, ?)", body.model_name, brand_id)
                model_id = cur.execute("SELECT MAX(model_id) FROM CarModels WHERE name = ? AND brand_id = ?", body.model_name, brand_id).fetchone()[0]

        photo_data = base64.b64decode(body.photo) if body.photo else None
        cur.execute("INSERT INTO Cars (user_id, model_id, mileage, photo) VALUES (?, ?, ?, ?)",
                    user_id, model_id, body.mileage, photo_data)
        car_id = cur.execute("SELECT MAX(car_id) FROM Cars WHERE user_id = ?", user_id).fetchone()[0]
        conn.commit()
    return {"car_id": car_id}


@app.put("/users/{user_id}/cars/{car_id}")
def update_car(user_id: int, car_id: int, body: CarUpdate):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT car_id FROM Cars WHERE car_id = ? AND user_id = ?",
            car_id, user_id,
        )
        if not cur.fetchone():
            raise HTTPException(status_code=404, detail="Автомобиль не найден")

        brand_id = body.brand_id
        if not brand_id and body.brand_name:
            cur.execute("SELECT brand_id FROM CarBrands WHERE name = ?", body.brand_name)
            r = cur.fetchone()
            brand_id = r[0] if r else None
            if not brand_id:
                cur.execute("INSERT INTO CarBrands (name) VALUES (?)", body.brand_name)
                brand_id = cur.execute("SELECT MAX(brand_id) FROM CarBrands WHERE name = ?", body.brand_name).fetchone()[0]

        model_id = body.model_id
        if not model_id and body.model_name and brand_id:
            cur.execute("SELECT model_id FROM CarModels WHERE name = ? AND brand_id = ?", body.model_name, brand_id)
            r = cur.fetchone()
            model_id = r[0] if r else None
            if not model_id:
                cur.execute("INSERT INTO CarModels (name, brand_id) VALUES (?, ?)", body.model_name, brand_id)
                model_id = cur.execute("SELECT MAX(model_id) FROM CarModels WHERE name = ? AND brand_id = ?", body.model_name, brand_id).fetchone()[0]

        fields, params = [], []
        if model_id is not None:
            fields.append("model_id = ?"); params.append(model_id)
        if body.mileage is not None:
            fields.append("mileage = ?"); params.append(body.mileage)
        if body.photo is not None:
            fields.append("photo = ?"); params.append(base64.b64decode(body.photo))

        if fields:
            params += [car_id, user_id]
            cur.execute(
                f"UPDATE Cars SET {', '.join(fields)} WHERE car_id = ? AND user_id = ?",
                *params,
            )
            conn.commit()
    return {"ok": True}


@app.delete("/users/{user_id}/cars/{car_id}")
def delete_car(user_id: int, car_id: int):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            "DELETE FROM Cars WHERE car_id = ? AND user_id = ?",
            car_id, user_id,
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="Автомобиль не найден")
        conn.commit()
    return {"ok": True}


# ─── brands & models ─────────────────────────────────────────────────────────

@app.get("/brands")
def get_brands():
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT brand_id, name FROM CarBrands ORDER BY name")
        rows = cur.fetchall()
    return [row_to_dict(cur, r) for r in rows]


@app.get("/brands/{brand_id}/models")
def get_models(brand_id: int):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT model_id, name FROM CarModels WHERE brand_id = ? ORDER BY name",
            brand_id,
        )
        rows = cur.fetchall()
    return [row_to_dict(cur, r) for r in rows]


# ─── refueling ───────────────────────────────────────────────────────────────

@app.get("/cars/{car_id}/refueling")
def get_refueling(car_id: int):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """SELECT r.refueling_id, r.date, r.mileage, r.volume, r.price_per_liter,
                      r.total_amount, r.full_tank, f.name AS fuel, g.name AS station
               FROM refueling r
               LEFT JOIN Fuel f ON r.fuel_id = f.fuel_id
               LEFT JOIN GasStations g ON r.station_id = g.station_id
               WHERE r.car_id = ?
               ORDER BY r.date DESC""",
            car_id,
        )
        rows = cur.fetchall()
    return [row_to_dict(cur, r) for r in rows]


@app.post("/cars/{car_id}/refueling", status_code=201)
def add_refueling(car_id: int, body: RefuelingCreate):
    sql_date = parse_date(body.date)
    total = round(body.volume * body.price_per_liter, 2)

    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT car_id FROM Cars WHERE car_id = ?", car_id)
        if not cur.fetchone():
            raise HTTPException(status_code=404, detail="Автомобиль не найден")

        station_id = body.station_id
        if not station_id and body.station_name:
            cur.execute("SELECT station_id FROM GasStations WHERE name = ?", body.station_name)
            r = cur.fetchone()
            if r:
                station_id = r[0]
            else:
                cur.execute("INSERT INTO GasStations (name) VALUES (?)", body.station_name)
                station_id = cur.execute("SELECT MAX(station_id) FROM GasStations WHERE name = ?", body.station_name).fetchone()[0]

        cur.execute(
            """INSERT INTO refueling
               (car_id, fuel_id, station_id, date, mileage, volume, price_per_liter, total_amount, full_tank)
               VALUES (?, ?, ?, CONVERT(DATETIME, ?, 112), ?, ?, ?, ?, ?)""",
            car_id, body.fuel_id, station_id, sql_date,
            body.mileage, body.volume, body.price_per_liter, total, body.full_tank,
        )
        ref_id = cur.execute("SELECT MAX(refueling_id) FROM refueling WHERE car_id = ?", car_id).fetchone()[0]

        cur.execute(
            "UPDATE Cars SET mileage = ? WHERE car_id = ? AND (mileage IS NULL OR mileage < ?)",
            body.mileage, car_id, body.mileage,
        )
        conn.commit()
    return {"refueling_id": ref_id}


@app.put("/cars/{car_id}/refueling/{refueling_id}")
def update_refueling(car_id: int, refueling_id: int, body: RefuelingUpdate):
    sql_date = parse_date(body.date)
    total = round(body.volume * body.price_per_liter, 2)

    with get_connection() as conn:
        cur = conn.cursor()

        station_id = body.station_id
        if not station_id and body.station_name:
            cur.execute("SELECT station_id FROM GasStations WHERE name = ?", body.station_name)
            r = cur.fetchone()
            if r:
                station_id = r[0]
            else:
                cur.execute("INSERT INTO GasStations (name) VALUES (?)", body.station_name)
                station_id = cur.execute("SELECT MAX(station_id) FROM GasStations WHERE name = ?", body.station_name).fetchone()[0]

        cur.execute(
            """UPDATE refueling
               SET fuel_id=?, station_id=?, date=CONVERT(DATETIME,?,112),
                   mileage=?, volume=?, price_per_liter=?, total_amount=?, full_tank=?
               WHERE refueling_id=? AND car_id=?""",
            body.fuel_id, station_id, sql_date,
            body.mileage, body.volume, body.price_per_liter, total, body.full_tank,
            refueling_id, car_id,
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="Запись не найдена")

        cur.execute(
            "UPDATE Cars SET mileage=? WHERE car_id=? AND (mileage IS NULL OR mileage < ?)",
            body.mileage, car_id, body.mileage,
        )
        conn.commit()
    return {"ok": True}


@app.delete("/cars/{car_id}/refueling/{refueling_id}")
def delete_refueling(car_id: int, refueling_id: int):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            "DELETE FROM refueling WHERE refueling_id = ? AND car_id = ?",
            refueling_id, car_id,
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="Запись не найдена")
        conn.commit()
    return {"ok": True}


# ─── fuel types & stations ───────────────────────────────────────────────────

@app.get("/fuels")
def get_fuels():
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT fuel_id, name, marking FROM Fuel ORDER BY name")
        rows = cur.fetchall()
    return [row_to_dict(cur, r) for r in rows]


@app.get("/stations")
def get_stations():
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT station_id, name FROM GasStations ORDER BY name")
        rows = cur.fetchall()
    return [row_to_dict(cur, r) for r in rows]


# ─── maintenance ─────────────────────────────────────────────────────────────

@app.get("/cars/{car_id}/maintenance")
def get_maintenance(car_id: int):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """SELECT m.maintenance_id, m.date, m.mileage, m.total_amount,
                      m.description, m.next_service_mileage, m.next_service_date,
                      st.name AS service_type, sc.name AS category
               FROM Maintenance m
               LEFT JOIN ServiceTypes st ON m.service_type_id = st.service_type_id
               LEFT JOIN ServiceCategory sc ON st.category_id = sc.category_id
               WHERE m.car_id = ?
               ORDER BY m.date DESC""",
            car_id,
        )
        rows = cur.fetchall()
    return [row_to_dict(cur, r) for r in rows]


@app.post("/cars/{car_id}/maintenance", status_code=201)
def add_maintenance(car_id: int, body: MaintenanceCreate):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT car_id FROM Cars WHERE car_id = ?", car_id)
        if not cur.fetchone():
            raise HTTPException(status_code=404, detail="Автомобиль не найден")

        cur.execute(
            """INSERT INTO Maintenance
               (car_id, service_type_id, date, mileage, total_amount,
                description, next_service_mileage, next_service_date)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            car_id, body.service_type_id, body.date, body.mileage,
            body.total_amount, body.description,
            body.next_service_mileage, body.next_service_date,
        )
        m_id = cur.execute("SELECT MAX(maintenance_id) FROM Maintenance WHERE car_id = ?", car_id).fetchone()[0]

        cur.execute(
            "UPDATE Cars SET mileage=? WHERE car_id=? AND (mileage IS NULL OR mileage < ?)",
            body.mileage, car_id, body.mileage,
        )
        conn.commit()
    return {"maintenance_id": m_id}


@app.put("/cars/{car_id}/maintenance/{maintenance_id}")
def update_maintenance(car_id: int, maintenance_id: int, body: MaintenanceUpdate):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """UPDATE Maintenance
               SET service_type_id=?, date=?, mileage=?, total_amount=?,
                   description=?, next_service_mileage=?, next_service_date=?
               WHERE maintenance_id=? AND car_id=?""",
            body.service_type_id, body.date, body.mileage, body.total_amount,
            body.description, body.next_service_mileage, body.next_service_date,
            maintenance_id, car_id,
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="Запись не найдена")
        conn.commit()
    return {"ok": True}


@app.delete("/cars/{car_id}/maintenance/{maintenance_id}")
def delete_maintenance(car_id: int, maintenance_id: int):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            "DELETE FROM Maintenance WHERE maintenance_id=? AND car_id=?",
            maintenance_id, car_id,
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="Запись не найдена")
        conn.commit()
    return {"ok": True}


# ─── statistics ──────────────────────────────────────────────────────────────

@app.get("/cars/{car_id}/statistics")
def get_statistics(car_id: int, date_from: str, date_to: str):
    """date_from / date_to in dd.MM.yyyy format"""
    df = parse_date(date_from)
    dt = parse_date(date_to)
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT
                COALESCE(SUM(fuel_costs), 0) + COALESCE(SUM(maint_costs), 0) AS total_expenses,
                COALESCE(SUM(fuel_costs), 0)  AS fuel_expenses,
                COALESCE(SUM(maint_costs), 0)  AS maintenance_expenses,
                COALESCE(MAX(total_mileage), 0) AS total_mileage
            FROM (
                SELECT SUM(r.total_amount) AS fuel_costs, NULL AS maint_costs,
                       MAX(r.mileage) - MIN(r.mileage) AS total_mileage
                FROM refueling r
                WHERE r.car_id = ?
                  AND CONVERT(date, r.date, 112) BETWEEN CONVERT(date, ?, 112) AND CONVERT(date, ?, 112)
                UNION ALL
                SELECT NULL AS fuel_costs, SUM(m.total_amount) AS maint_costs, NULL AS total_mileage
                FROM Maintenance m
                WHERE m.car_id = ?
                  AND CONVERT(date, m.date, 112) BETWEEN CONVERT(date, ?, 112) AND CONVERT(date, ?, 112)
            ) combined
            """,
            car_id, df, dt, car_id, df, dt,
        )
        row = cur.fetchone()
    if not row:
        return {"total_expenses": 0, "fuel_expenses": 0, "maintenance_expenses": 0, "total_mileage": 0}
    return row_to_dict(cur, row)


@app.get("/cars/{car_id}/statistics/monthly")
def get_monthly_statistics(car_id: int, date_from: str, date_to: str):
    df = parse_date(date_from)
    dt = parse_date(date_to)
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT month,
                   SUM(fuel_amount) + SUM(maint_amount) AS total_amount,
                   SUM(fuel_amount)  AS fuel_amount,
                   SUM(maint_amount) AS maintenance_amount
            FROM (
                SELECT FORMAT(CONVERT(date, r.date, 112), 'yyyy-MM') AS month,
                       SUM(r.total_amount) AS fuel_amount, 0 AS maint_amount
                FROM refueling r
                WHERE r.car_id = ?
                  AND CONVERT(date, r.date, 112) BETWEEN CONVERT(date, ?, 112) AND CONVERT(date, ?, 112)
                GROUP BY FORMAT(CONVERT(date, r.date, 112), 'yyyy-MM')
                UNION ALL
                SELECT FORMAT(CONVERT(date, m.date, 112), 'yyyy-MM') AS month,
                       0 AS fuel_amount, SUM(m.total_amount) AS maint_amount
                FROM Maintenance m
                WHERE m.car_id = ?
                  AND CONVERT(date, m.date, 112) BETWEEN CONVERT(date, ?, 112) AND CONVERT(date, ?, 112)
                GROUP BY FORMAT(CONVERT(date, m.date, 112), 'yyyy-MM')
            ) monthly
            GROUP BY month
            ORDER BY month DESC
            """,
            car_id, df, dt, car_id, df, dt,
        )
        rows = cur.fetchall()
    return [row_to_dict(cur, r) for r in rows]


@app.get("/cars/{car_id}/statistics/fuel-consumption")
def get_fuel_consumption(car_id: int, date_from: str, date_to: str):
    df = parse_date(date_from)
    dt = parse_date(date_to)
    with get_connection() as conn:
        cur = conn.cursor()
        # Try full-tank calculation first
        cur.execute(
            """
            WITH ft AS (
                SELECT mileage, volume, total_amount,
                       LAG(mileage) OVER (ORDER BY date, mileage) AS prev_mileage
                FROM refueling
                WHERE car_id = ? AND full_tank = 1
                  AND CONVERT(date, date, 112) BETWEEN CONVERT(date, ?, 112) AND CONVERT(date, ?, 112)
            )
            SELECT
                SUM(CASE WHEN mileage > prev_mileage AND prev_mileage IS NOT NULL THEN mileage - prev_mileage ELSE 0 END) AS total_distance,
                SUM(volume) AS total_volume,
                SUM(total_amount) AS total_fuel_cost,
                COUNT(*) AS full_tank_count
            FROM ft
            """,
            car_id, df, dt,
        )
        row = cur.fetchone()
        d = row_to_dict(cur, row) if row else {}
        total_distance = d.get("total_distance") or 0
        full_tank_count = d.get("full_tank_count") or 0

        if full_tank_count >= 2 and total_distance > 0:
            total_volume = d.get("total_volume") or 0
            total_fuel_cost = d.get("total_fuel_cost") or 0
            return {
                "avg_consumption": (total_volume / total_distance) * 100,
                "cost_per_km": total_fuel_cost / total_distance,
                "distance": total_distance,
                "total_volume": total_volume,
                "total_fuel_cost": total_fuel_cost,
            }

        # Fallback: all refueling records
        cur.execute(
            """
            SELECT COALESCE(MAX(mileage) - MIN(mileage), 0) AS total_distance,
                   COALESCE(SUM(volume), 0) AS total_volume,
                   COALESCE(SUM(total_amount), 0) AS total_fuel_cost
            FROM refueling
            WHERE car_id = ?
              AND CONVERT(date, date, 112) BETWEEN CONVERT(date, ?, 112) AND CONVERT(date, ?, 112)
            """,
            car_id, df, dt,
        )
        row2 = cur.fetchone()
        d2 = row_to_dict(cur, row2) if row2 else {}
        dist = d2.get("total_distance") or 0
        vol = d2.get("total_volume") or 0
        cost = d2.get("total_fuel_cost") or 0
        return {
            "avg_consumption": (vol / dist * 100) if dist > 0 else 0.0,
            "cost_per_km": (cost / dist) if dist > 0 else 0.0,
            "distance": dist,
            "total_volume": vol,
            "total_fuel_cost": cost,
        }


# ─── service types ───────────────────────────────────────────────────────────

@app.get("/service-types")
def get_service_types():
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """SELECT st.service_type_id, st.name, st.interval_km,
                      sc.category_id, sc.name AS category
               FROM ServiceTypes st
               LEFT JOIN ServiceCategory sc ON st.category_id = sc.category_id
               ORDER BY sc.category_id, st.name""",
        )
        rows = cur.fetchall()
    return [row_to_dict(cur, r) for r in rows]


# ─── user profile ────────────────────────────────────────────────────────────

class ProfileUpdate(BaseModel):
    full_name: str
    username: str
    email: str

class PasswordUpdate(BaseModel):
    current_password: str
    new_password: str


@app.put("/users/{user_id}/profile")
def update_profile(user_id: int, body: ProfileUpdate):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT 1 FROM users WHERE username = ? AND user_id != ?",
            body.username, user_id,
        )
        if cur.fetchone():
            raise HTTPException(status_code=409, detail="Логин уже занят")
        cur.execute(
            "UPDATE users SET full_name=?, username=?, email=? WHERE user_id=?",
            body.full_name, body.username, body.email, user_id,
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="Пользователь не найден")
        conn.commit()
    return {"ok": True}


@app.put("/users/{user_id}/password")
def update_password(user_id: int, body: PasswordUpdate):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT password FROM users WHERE user_id=?", user_id)
        row = cur.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Пользователь не найден")
        if not verify_password(body.current_password, row[0]):
            raise HTTPException(status_code=401, detail="Неверный текущий пароль")
        cur.execute(
            "UPDATE users SET password=? WHERE user_id=?",
            hash_password(body.new_password), user_id,
        )
        conn.commit()
    return {"ok": True}


@app.delete("/users/{user_id}")
def delete_user(user_id: int):
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute("DELETE FROM users WHERE user_id=?", user_id)
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="Пользователь не найден")
        conn.commit()
    return {"ok": True}
