from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime

class Marker(BaseModel):
    id: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    name: Optional[str] = None
    description: Optional[str] = None
    address: Optional[str] = None
    region: Optional[str] = None
    type: Optional[str] = None
    status: Optional[str] = "운영 중"
    last_updated: Optional[datetime] = None
    created_at: Optional[datetime] = None
    amenities: Optional[List[str]] = None
    capacity: Optional[int] = None
    image_url: Optional[str] = None
    rating: Optional[float] = None
    reviews: Optional[List[str]] = None

class MarkerUpdate(BaseModel):
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    name: Optional[str] = None
    description: Optional[str] = None
    address: Optional[str] = None
    region: Optional[str] = None
    type: Optional[str] = None
    status: Optional[str] = None
    amenities: Optional[List[str]] = None
    capacity: Optional[int] = None
    image_url: Optional[str] = None
    rating: Optional[float] = None
    reviews: Optional[List[str]] = None
