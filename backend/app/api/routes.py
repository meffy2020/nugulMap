from fastapi import APIRouter
from app.models.marker import Marker
from app.core.firebase import db
import uuid
from datetime import datetime

router = APIRouter()

@router.post("/marker")
def create_marker(marker: Marker):
    doc_id = str(uuid.uuid4())
    # Firestore에 저장할 데이터
    data = marker.dict()
    data["created_at"] = data.get("created_at") or datetime.utcnow()  # 생성 시간 추가
    data["last_updated"] = datetime.utcnow()  # 마지막 업데이트 시간 추가
    db.collection("markers").document(doc_id).set(data)
    return {"status": "saved", "id": doc_id}

@router.get("/marker")
def get_markers():
    docs = db.collection("markers").stream()
    markers = []
    for doc in docs:
        try:
            data = doc.to_dict()
            if data.get("latitude") is None or data.get("longitude") is None or data.get("name") is None:
                continue
            last_updated = data.get("last_updated")
            created_at = data.get("created_at")
            if hasattr(last_updated, "to_datetime"):
                last_updated = last_updated.to_datetime()
            if hasattr(created_at, "to_datetime"):
                created_at = created_at.to_datetime()
            marker = Marker(
                id=doc.id,
                latitude=data.get("latitude"),
                longitude=data.get("longitude"),
                name=data.get("name"),
                description=data.get("description"),
                address=data.get("address"),
                region=data.get("region"),
                type=data.get("type"),
                status=data.get("status", "운영 중"),
                last_updated=last_updated,
                created_at=created_at,
                amenities=data.get("amenities"),
                capacity=data.get("capacity"),
                image_url=data.get("image_url"),
                rating=data.get("rating"),
                reviews=data.get("reviews"),
            )
            markers.append(marker.dict())
        except Exception as e:
            print(f"Error parsing marker: {e}")
            continue
    return {"markers": markers}