from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime

class Marker(BaseModel):
    id: Optional[str]  # Firestore에서 자동 생성될 수 있음
    latitude: float
    longitude: float
    name: str
    description: Optional[str]
    address: Optional[str]
    region: Optional[str]
    type: Optional[str]
    status: Optional[str] = "운영 중"
    last_updated: Optional[datetime]
    created_at: Optional[datetime]
    amenities: Optional[List[str]]
    capacity: Optional[int]
    image_url: Optional[str]
    rating: Optional[float]
    reviews: Optional[List[str]]