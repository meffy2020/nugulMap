from fastapi import APIRouter
from app.models.marker import Marker

router = APIRouter()

db = []  # 임시 DB

@router.post("/marker")
def create_marker(marker: Marker):
    db.append(marker)
    return {"status": "saved", "marker": marker}

@router.get("/marker")
def get_markers():
    return {"markers": db}