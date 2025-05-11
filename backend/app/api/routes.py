from fastapi import APIRouter
from app.models.marker import Marker
from app.core.firebase import db
import uuid

router = APIRouter()

@router.post("/marker")
def create_marker(marker: Marker):
    doc_id = str(uuid.uuid4())
    db.collection("markers").document(doc_id).set(marker.dict())
    return {"status": "saved", "id": doc_id}

@router.get("/marker")
def get_markers():
    docs = db.collection("markers").stream()
    return {"markers": [doc.to_dict() for doc in docs]}