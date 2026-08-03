# Aira API. Build:  docker build -t aira-api .
# Run:   docker run -p 8000:8000 --env-file .env aira-api
#
# ENV=production activates the startup guard (config.py): the container
# REFUSES to boot on placeholder secrets, a missing Gemini key, or a missing
# DATABASE_URL. That is intended — fix the env, not the guard.
FROM python:3.12-slim

WORKDIR /app

# psycopg needs libpq at runtime when DATABASE_URL is set.
RUN apt-get update && apt-get install -y --no-install-recommends libpq5 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir "psycopg[binary]>=3.1"

COPY *.py ./

# Never bake secrets, databases, or uploads into the image.
# (.env, *.db and uploads/ are also excluded via .dockerignore.)

EXPOSE 8000
ENV ENV=production
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
