from fastapi import APIRouter, HTTPException, status
from app.models.marker import Marker, MarkerUpdate
from app.core.firebase import db
import uuid
from datetime import datetime
import logging

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

router = APIRouter()

@router.post("/marker", status_code=status.HTTP_201_CREATED)
async def create_marker(marker: Marker):
    """새로운 마커를 생성합니다."""
    try:
        doc_id = str(uuid.uuid4())
        data = marker.dict(exclude_unset=True)
        data["id"] = doc_id
        data["created_at"] = datetime.utcnow()
        data["last_updated"] = datetime.utcnow()
        
        db.collection("markers").document(doc_id).set(data)
        logger.info(f"Marker created with ID: {doc_id}")
        return {"status": "saved", "id": doc_id}
    except Exception as e:
        logger.error(f"Error creating marker: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal Server Error")

@router.get("/marker")
async def get_markers(skip: int = 0, limit: int = 100):
    """모든 마커를 페이지네이션하여 가져옵니다."""
    try:
        docs_query = db.collection("markers").limit(limit).offset(skip)
        docs = docs_query.stream()
        
        markers = []
        for doc in docs:
            markers.append(doc.to_dict())
            
        return {"markers": markers}
    except Exception as e:
        logger.error(f"Error getting markers: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal Server Error")

@router.get("/marker/{marker_id}")
async def get_marker(marker_id: str):
    """특정 ID의 마커를 가져옵니다."""
    try:
        doc_ref = db.collection("markers").document(marker_id)
        doc = doc_ref.get()
        if not doc.exists:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Marker not found")
        return doc.to_dict()
    except HTTPException as he:
        raise he
    except Exception as e:
        logger.error(f"Error getting marker {marker_id}: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal Server Error")

@router.put("/marker/{marker_id}")
async def update_marker(marker_id: str, marker_update: MarkerUpdate):
    """특정 ID의 마커를 업데이트합니다."""
    try:
        doc_ref = db.collection("markers").document(marker_id)
        doc = doc_ref.get()
        if not doc.exists:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Marker not found")
        
        update_data = marker_update.dict(exclude_unset=True)
        update_data["last_updated"] = datetime.utcnow()
        
        doc_ref.update(update_data)
        logger.info(f"Marker updated with ID: {marker_id}")
        return {"status": "updated", "id": marker_id}
    except HTTPException as he:
        raise he
    except Exception as e:
        logger.error(f"Error updating marker {marker_id}: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal Server Error")

@router.delete("/marker/{marker_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_marker(marker_id: str):
    """특정 ID의 마커를 삭제합니다."""
    try:
        doc_ref = db.collection("markers").document(marker_id)
        doc = doc_ref.get()
        if not doc.exists:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Marker not found")
            
        doc_ref.delete()
        logger.info(f"Marker deleted with ID: {marker_id}")
        return
    except HTTPException as he:
        raise he
    except Exception as e:
        logger.error(f"Error deleting marker {marker_id}: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal Server Error")
