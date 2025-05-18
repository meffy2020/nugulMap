from app.api.routes import router
from fastapi import FastAPI

app = FastAPI()
app.include_router(router)