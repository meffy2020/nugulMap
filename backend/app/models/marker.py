from pydantic import BaseModel

class Marker(BaseModel):
    lat: float
    lng: float
    description: str