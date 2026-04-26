import os
from ultralytics import YOLO
from roboflow import Roboflow

def prepare_data():
    rf = Roboflow(api_key="")
    project = rf.workspace("myworkspace-2s1f5").project("eyepay-zninh")
    version = project.version(2)
    dataset = version.download("yolov8")
    return os.path.join(dataset.location, "data.yaml")

def train_model(data_yaml_path):
    model = YOLO('yolo26n.pt') 

    results = model.train(
        data=data_yaml_path,
        epochs=150,
        imgsz=640,
        batch=16,
        device='mps',
        patience=50,
        optimizer='AdamW',
        lr0=0.001,
        cos_lr=True,
        label_smoothing=0.1,
        project='eyepay_local',
        name='eyepay_model',
        exist_ok=True
    )
    
    return model
    
def export_model(model):
    model.export(format='tflite', half=True)

if __name__ == "__main__":
    yaml_path = prepare_data()
    best_model = train_model(yaml_path)
    export_model(best_model)