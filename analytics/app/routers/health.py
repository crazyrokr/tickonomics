import numpy
import scipy
import statsmodels
from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
async def health():
    return {
        "status": "UP",
        "scipy_version": scipy.__version__,
        "statsmodels_version": statsmodels.__version__,
        "numpy_version": numpy.__version__,
    }
