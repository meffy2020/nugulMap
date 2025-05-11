from fastapi import APIRouter

router = APIRouter()

@router.get("/")
def read_root():
    return {"message": "너굴맵 FastAPI 백엔드 준비 완료!"}