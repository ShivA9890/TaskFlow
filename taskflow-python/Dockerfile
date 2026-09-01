FROM python:3.12-slim

ENV PYTHONNUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1

WORKDIR /app

COPY requirements.txt ./

RUN pip install --no-cache-dir -r requirements.txt

COPY common/ ./common/
COPY activity_service/ ./activity_service/
COPY notification_service/ ./notification_service/
COPY outbox_poller/ ./outbox_poller/
COPY reminder_job/ ./reminder_job/

ENTRYPOINT ["python","-m"]
CMD [outbox_poller.poller]
