from pydantic import BaseModel

class Marker(BaseModel):
    id: int
    lat: float
    lng: float
    description: str