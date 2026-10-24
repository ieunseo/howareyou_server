# nlp_service/analysisTag.py
import os
from datetime import datetime
from typing import List, Dict

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer, util
from pymongo import MongoClient, UpdateOne

app = FastAPI(title="NLP Tagging Service")

@app.get("/health")
def health():
    return {"ok": True}

# ===== MongoDB 연결 =====
import os
from pymongo import MongoClient

# ===== MongoDB 연결 =====
# MONGO_URI = os.getenv(
#     "MONGO_URI",
#     "mongodb+srv://les67030500_db_user:updvuRAltHnB2o7t@cluster0.49hfynz.mongodb.net/?appName=Cluster0"
# )
# MONGO_DB  = os.getenv("MONGO_DB", "howrudb")
#
# client = MongoClient(
#     MONGO_URI,
#     socketTimeoutMS=20000,
#     connectTimeoutMS=20000,
#     serverSelectionTimeoutMS=20000,
#     tls=True,              # SRV는 기본 TLS지만 명시해도 무방
#     retryWrites=True
# )
# db = client[MONGO_DB]
# ===== MongoDB 연결 =====
MONGO_URI = os.environ["MONGO_URI"]
MONGO_DB = os.environ["MONGO_DB"]

client = MongoClient(
    MONGO_URI,
    socketTimeoutMS=20000,
    connectTimeoutMS=20000,
    serverSelectionTimeoutMS=20000,
    tls=True,
    retryWrites=True,
)

db = client[MONGO_DB]

# 컬렉션 이름 (필요시 변경)
VOCAB_COLL = "chatroom_vocabulary"   # 예: { memberId: 123, words: ["pizza", "travel", ...] }
SCORES_COLL = "member_tag_score"    # 저장: { memberId: 123, scores: { "음식 & 요리": 1.75, ... }, updatedAt: ... }


# ===== 태그 + 설명 =====
tag_descriptions = {
    "LANGUAGE_LEARNING": "Languages & Cultural Exchange: language, foreign language, translation, bilingual, multilingual, language exchange, culture exchange",
    "TRAVEL": "Travel & Tourism: travel, trip, tour, tourism, destination, vacation, holiday, beach, island, mountain, hiking, sightseeing",
    "CULTURE": "Culture & Arts: traditional culture, performance, art, painting, sculpture, museum, literature, poetry, novel, exhibition, theater, drama, festival",
    "BUSINESS": "Business & Startups: business, startup, entrepreneurship, entrepreneur, marketing, sales, management, company, corporate",
    "EDUCATION": "Education & Learning: education, study, studying, school, learning, course, online course, lecture, class, training, self-development",
    "TECHNOLOGY": "Technology & IT: technology, computer, software, hardware, programming, coding, artificial intelligence, AI, robotics, gadget, innovation",
    "SPORTS": "Sports: sport, soccer, football, basketball, baseball, tennis, golf, fitness, gym, yoga, swimming, running",
    "MUSIC": "Music: music, song, track, album, band, instrument, guitar, piano, concert, festival, performance",
    "FOOD": "Food & Cooking: cooking, recipe, chef, cuisine, meal, dish, ingredient, gastronomy, dining, restaurant, kitchen, baking",
    "ART": "Stationery & Design: stationery, pen, paper, notebook, illustration, graphic design, drawing, sketch, typography",
    "SCIENCE": "Science: science, scientific, physics, chemistry, biology, astronomy, geology, experiment, research",
    "HISTORY": "History: history, historical, ancient history, modern history, heritage, war, civilization, historical event",
    "MOVIES": "Entertainment: entertainment, movie, film, cinema, drama, anime, animation, concert, musical, show, performance",
    "GAMES": "Games: game, gaming, console, pc game, mobile game, playstation, xbox, nintendo, esports, online game",
    "LITERATURE": "Literature: reading, writing, novel, poetry, prose, book, essay, fiction, literature analysis",
    "PHOTOGRAPHY": "Photography & Videography: photo, photography, picture, camera, filming, video, videography, shoot",
    "NATURE": "Environment & Nature: environment, environmental, nature, climate, climate change, eco-friendly, wildlife, recycling, conservation",
    "FITNESS": "Health & Wellness: health, fitness, diet, nutrition, meditation, mental health, wellness, exercise, workout, healthy lifestyle",
    "FASHION": "Fashion & Beauty: fashion, clothing, style, outfit, dress, makeup, cosmetics, skincare, beauty products, hairstyle, modeling",
    "VOLUNTEERING": "Volunteering & Community: volunteering, nonprofit, donation, community service, helping others",
    "ANIMALS": "Pets & Animals: pet, dog, puppy, cat, kitten, animal, animal care, companion animal",
    "CARS": "Automobiles & Mobility: automobile, car, vehicle, bike, motorcycle, transportation, driving, road trip",
    "DIY": "DIY & Hobbies: hobby, crafting, craft, diy, gardening, plant, collecting, leisure, lifestyle",
    "FINANCE": "Economy & Finance: economy, economic, finance, financial, investment, stock, trading, market, business, real estate, banking"
}

tag_keywords = {
    "LANGUAGE_LEARNING": [
        "language", "translation", "bilingual", "foreign", "multilingual", "exchange",
        "speaking", "conversation", "grammar", "pronunciation", "vocabulary",
        "language partner", "fluency", "language test", "TOEIC", "IELTS", "language app"
    ],
    "TRAVEL": [
        "travel", "trip", "tour", "destination", "vacation", "beach", "mountain", "hiking",
        "backpacking", "airbnb", "flight", "passport", "itinerary", "exploration",
        "hotel", "hostel", "sightseeing", "guide"
    ],
    "CULTURE": [
        "culture", "museum", "festival", "tradition", "performance",
        "heritage", "ceremony", "ritual", "cultural event", "custom",
        "ethnicity", "folk", "dance", "symbol", "belief", "tradition", "language", "celebration"
    ],
    "BUSINESS": [
        "business", "startup", "entrepreneur", "company", "marketing", "management",
        "investment", "pitch", "profit", "corporate", "founder", "team", "revenue", "strategy",
        "accelerator", "incubator", "business plan", "scaling"
    ],
    "EDUCATION": [
        "education", "study", "learning", "lecture", "school", "course",
        "university", "exam", "assignment", "classroom", "teacher", "textbook",
        "tutoring", "e-learning", "quiz", "homework", "degree", "curriculum"
    ],
    "TECHNOLOGY": [
        "technology", "software", "hardware", "ai", "robotics", "innovation", "coding", "programming",
        "machine learning", "cloud", "server", "frontend", "backend", "algorithm",
        "blockchain", "data", "api", "mobile tech"
    ],
    "SPORTS": [
        "sport", "soccer", "tennis", "basketball", "golf", "swimming", "yoga", "gym",
        "athlete", "football", "volleyball", "running", "baseball", "training", "competition",
        "league", "tournament", "team"
    ],
    "MUSIC": [
        "music", "song", "band", "instrument", "concert", "album",
        "melody", "lyrics", "guitar", "piano", "drums", "rap", "chorus", "composition",
        "recording", "studio", "performance", "genre"
    ],
    "FOOD": [
        "food", "cooking", "recipe", "chef", "ingredient", "meal", "kitchen",
        "taste", "dish", "flavor", "baking", "snack", "dining", "restaurant",
        "menu", "grocery", "nutrition", "culinary"
    ],
    "ART": [
        "art", "drawing", "painting", "illustration", "sketch", "design", "graphic",
        "canvas", "color", "gallery", "sculpture", "brush", "artist", "exhibit",
        "calligraphy", "installation", "visual art", "aesthetic"
    ],
    "SCIENCE": [
        "science", "physics", "chemistry", "biology", "research", "experiment", "astronomy",
        "theory", "microscope", "hypothesis", "scientist", "genetics", "lab", "data",
        "ecology", "evolution", "element", "discovery"
    ],
    "HISTORY": [
        "history", "heritage", "war", "ancient", "monument", "civilization",
        "historical", "timeline", "event", "empire", "dynasty", "relic", "archaeology",
        "battle", "archive", "colonial", "revolution", "historian"
    ],
    "MOVIES": [
        "movie", "film", "drama", "cinema", "animation", "show",
        "director", "actor", "scene", "script", "character", "theater",
        "box office", "genre", "trailer", "blockbuster", "festival", "award"
    ],
    "GAMES": [
        "game", "gaming", "console", "pc game", "mobile game", "esports",
        "strategy", "fps", "rpg", "level", "quest", "multiplayer", "tournament",
        "rank", "controller", "steam", "gamer"
    ],
    "LITERATURE": [
        "book", "novel", "essay", "poetry", "fiction", "reading", "writing",
        "author", "manuscript", "prose", "literary", "genre", "story", "narrative",
        "publication", "editor", "literature", "critique"
    ],
    "PHOTOGRAPHY": [
        "photo", "camera", "video", "shoot", "filming", "lens",
        "shutter", "exposure", "focus", "tripod", "edit", "dslr", "gallery",
        "portrait", "composition", "angle", "frame", "filter"
    ],
    "NATURE": [
        "nature", "environment", "climate", "wildlife", "recycling", "eco",
        "green", "forest", "mountain", "river", "park", "sustainability",
        "tree", "natural", "outdoors", "weather", "landscape", "ecology"
    ],
    "FITNESS": [
        "fitness", "health", "diet", "exercise", "wellness", "meditation", "workout",
        "training", "routine", "cardio", "gym", "weight", "strength", "yoga",
        "calories", "hydration", "stretch", "fit"
    ],
    "FASHION": [
        "fashion", "style", "clothing", "makeup", "skincare", "cosmetics",
        "outfit", "accessory", "model", "trend", "wardrobe", "designer",
        "shopping", "fabric", "lookbook", "eyeshadow", "lipstick", "runway"
    ],
    "VOLUNTEERING": [
        "volunteer", "help", "donation", "nonprofit", "support",
        "service", "community", "charity", "ngo", "campaign", "fundraising",
        "assist", "mentor", "relief", "care", "aid", "impact", "giving"
    ],
    "ANIMALS": [
        "pet", "dog", "cat", "animal", "kitten", "puppy",
        "veterinary", "shelter", "adopt", "fur", "leash", "animal care",
        "rescue", "bark", "meow", "training", "wildlife", "pet food"
    ],
    "CARS": [
        "car", "automobile", "vehicle", "motorcycle", "transportation", "drive",
        "engine", "tire", "garage", "fuel", "license", "traffic", "sedan",
        "highway", "ride", "repair", "speed", "driver"
    ],
    "DIY": [
        "diy", "hobby", "craft", "gardening", "leisure", "collecting",
        "tool", "woodworking", "homemade", "decor", "glue", "paint",
        "project", "fixing", "handmade", "build", "plant", "kit"
    ],
    "FINANCE": [
        "finance", "investment", "stock", "saving", "budget", "money", "economy",
        "market", "income", "asset", "expense", "bank", "trading",
        "credit", "debt", "financial plan", "retirement", "interest"
    ]
}

# ===== 임베딩 모델/태그 임베딩 =====
model = SentenceTransformer("all-MiniLM-L6-v2")
tag_embeddings = {tag: model.encode(desc, convert_to_tensor=True)
                  for tag, desc in tag_descriptions.items()}

# ===== 요청/응답 모델 =====
class ClassifyRequest(BaseModel):
    words: List[str]

class ClassifyResponse(BaseModel):
    scores: Dict[str, float]

# ===== 분류 로직 =====
def classify_word(word: str, threshold=0.2, top_k=3):
    # 키워드 직접 매칭
    for tag, keywords in tag_keywords.items():
        if word.lower() in (kw.lower() for kw in keywords):
            return [(tag, 1.0)]

    wv = model.encode(word, convert_to_tensor=True)
    sims = [(tag, float(util.cos_sim(wv, tv).item())) for tag, tv in tag_embeddings.items()]
    sims.sort(key=lambda x: x[1], reverse=True)
    weights = [1.0, 0.5, 0.25]
    out = []
    for i, (tag, s) in enumerate(sims[:top_k]):
        if s >= threshold:
            out.append((tag, s * weights[i]))
    return out

def classify_words(words: List[str]) -> Dict[str, float]:
    agg = {tag: 0.0 for tag in tag_descriptions.keys()}
    for w in words:
        for tag, sc in classify_word(w):
            agg[tag] += sc
    return {k: round(v, 4) for k, v in agg.items() if v > 0}

@app.post("/classify", response_model=ClassifyResponse)
def classify(req: ClassifyRequest):
    scores = classify_words(req.words)
    return ClassifyResponse(scores=scores)

# ===== MongoDB 연동: memberId 기준으로 읽고/저장 =====
def fetch_vocab_words(member_id: int) -> List[str]:
    """
    chatroom_vocabulary 컬렉션에서 memberId별 단어 리스트를 가져온다고 가정.
    스키마 예시:
      { _id: ..., memberId: 123, words: ["pizza","travel","coffee"] }
    여러 문서가 있으면 words를 합칩니다.
    """
    cursor = db[VOCAB_COLL].find({"memberId": member_id}, {"words": 1})
    words: List[str] = []
    for doc in cursor:
        ws = doc.get("words") or []
        words.extend(ws)
    # 중복 제거(선택)
    return list(dict.fromkeys(words))

def save_member_scores(member_id: int, scores: Dict[str, float]) -> None:
    """
    member_tag_scores 컬렉션에 upsert 저장.
    문서 스키마:
      { memberId: 123, scores: { "음악": 1.5, ... }, updatedAt: ISODate(...) }
    """
    db[SCORES_COLL].update_one(
        {"memberId": member_id},
        {"$set": {"scores": scores, "updatedAt": datetime.utcnow()}},
        upsert=True
    )

@app.get("/members/{member_id}/classify", response_model=ClassifyResponse)
def classify_member(member_id: int):
    words = fetch_vocab_words(member_id)
    if not words:
        raise HTTPException(status_code=404, detail="No vocabulary found for member")
    scores = classify_words(words)
    save_member_scores(member_id, scores)
    return ClassifyResponse(scores=scores)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
