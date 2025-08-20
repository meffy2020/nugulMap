from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

# 테스트용 로컬 Docker DB 연결 정보
DB_USER = "root"
DB_PASSWORD = "root"
DB_HOST = "127.0.0.1" # localhost
DB_PORT = "3307"
DB_NAME = "mydb"

DATABASE_URL = f"mysql+mysqlconnector://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"

# SQLAlchemy 엔진 생성
engine = create_engine(DATABASE_URL)

# SQLAlchemy 세션 생성
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# 베이스 클래스 생성 (ORM 모델들이 상속받을 클래스)
Base = declarative_base()

def get_db():
    """
    FastAPI 의존성 주입을 위한 데이터베이스 세션 생성 함수
    """
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
