from sqlalchemy import Column, Integer, String, Float, Text, Date
from app.core.db import Base

class Zone(Base):
    __tablename__ = "zone"

    id = Column(Integer, primary_key=True, index=True)
    region = Column(String(100), nullable=False)
    type = Column(String(50))
    subtype = Column(String(50))
    description = Column(Text) # CLOB is represented as Text
    latitude = Column(Float(precision=10, asdecimal=True), nullable=False) # Using Float to represent DECIMAL
    longitude = Column(Float(precision=10, asdecimal=True), nullable=False) # Using Float to represent DECIMAL
    size = Column(String(50))
    date = Column(Date, nullable=False)
    address = Column(String(100), nullable=False, unique=True)
    user = Column(String(100))
    image = Column(String(255))
