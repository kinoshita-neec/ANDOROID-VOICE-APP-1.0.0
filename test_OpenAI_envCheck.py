import os

# 環境変数からAPIキーを取得
api_key = os.getenv("OPENAI_API_KEY")

# APIキーが正しく取得できているか確認
if api_key is None:
    raise ValueError("APIキーが設定されていません。環境変数に 'OPENAI_API_KEY' を追加してください。")
else:
    print(f"環境変数にAPIキーが正しく設定されています")