from pydantic import BaseModel
from typing import Optional


# Auth
class LoginRequest(BaseModel):
    username: str
    password: str

class RegisterRequest(BaseModel):
    full_name: str
    email: str
    username: str
    password: str

class UserResponse(BaseModel):
    user_id: int
    full_name: str
    username: str
    email: Optional[str] = None


# Cars
class CarCreate(BaseModel):
    brand_id: Optional[int] = None
    brand_name: Optional[str] = None
    model_id: Optional[int] = None
    model_name: Optional[str] = None
    mileage: float
    photo: Optional[bytes] = None

class CarUpdate(BaseModel):
    brand_id: Optional[int] = None
    brand_name: Optional[str] = None
    model_id: Optional[int] = None
    model_name: Optional[str] = None
    mileage: Optional[float] = None
    photo: Optional[bytes] = None


# Refueling
class RefuelingCreate(BaseModel):
    fuel_id: int
    station_id: Optional[int] = None
    station_name: Optional[str] = None
    date: str  # dd.MM.yyyy
    mileage: float
    volume: float
    price_per_liter: float
    full_tank: bool

class RefuelingUpdate(RefuelingCreate):
    pass


# Maintenance
class MaintenanceCreate(BaseModel):
    service_type_id: int
    date: str  # dd.MM.yyyy
    mileage: int
    total_amount: float
    description: Optional[str] = None
    next_service_mileage: Optional[int] = None
    next_service_date: Optional[str] = None

class MaintenanceUpdate(MaintenanceCreate):
    pass
