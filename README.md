# Beat Map · Four Corners

네 모서리의 드럼 패턴을 직접 찍고, 그 사이의 패턴을 마우스로 연주하는 4/4 악기입니다.

## 실행 환경

macOS에서 [Processing 4](https://processing.org/download)와 [Wekinator](https://github.com/fiebrink1/wekinator)를 사용합니다. Wekinator에서 `CornerModel/CornerModel.wekproj`를 열어 **Run**을 누릅니다. Processing에서 `BeatMap/BeatMap.pde`를 열고 ▶를 눌러 드럼 창을 엽니다. 프로젝트 파일은 폴더 구조를 유지해 내려받아야 합니다.

`BeatMap.command`는 프로젝트를 자동으로 열 수 있도록 구성된 Mac에서 사용할 수 있습니다. 자동 실행이 되지 않으면 위의 방법으로 두 앱을 각각 엽니다.

## 연주와 편집

1. 드럼 창에 `WEKINATOR LIVE`가 표시되면 **Play**를 누릅니다. 스페이스바로도 재생과 정지를 할 수 있습니다.
2. 패드 안에서 마우스를 움직여 기본 패턴 네 개를 들어봅니다. 화면 아래 16칸의 밝기가 각 타격의 세기입니다.
3. 오른쪽 **Edit a corner**에서 A, B, C, D 중 하나를 누릅니다. 아래 격자에서 킥·스네어·하이햇의 칸을 클릭해 켜거나 끕니다. 같은 칸을 클릭할 때마다 강하게, 약하게, 쉼 순으로 바뀝니다. 옆의 **− / +** 버튼으로 그 모서리의 템포도 조절할 수 있습니다. **Play**를 누르면 편집 중인 모서리가 재생됩니다.
4. **Train & play**를 누릅니다. Wekinator가 네 모서리 전체를 다시 학습하고, 준비되면 패드 연주 모드로 돌아와 재생을 시작합니다. 모서리에서 자신의 패턴을 듣고 패드 가운데에서 타격이 섞이는 걸 확인하세요.

편집 내용은 `BeatMap/data/corners.csv`에 즉시 저장됩니다. 예제를 다시 열면 저장된 네 패턴으로 모델을 다시 학습합니다. `WAITING FOR WEKINATOR`가 계속 뜨면 Wekinator에서 `CornerModel/CornerModel.wekproj`가 열려 있는지 확인한 뒤 **Run**을 누르세요. Processing에서 직접 열 때는 `BeatMap/BeatMap.pde`를 열고 ▶를 누릅니다.

## 회귀 모델

Processing은 패드의 X 좌표, Y 좌표, X×Y를 입력으로 보냅니다. Wekinator는 킥·스네어·하이햇의 16스텝 타격 세기 48개와 템포 하나를 독립적인 선형 회귀로 학습합니다. 같은 킥이라도 스텝마다 결과가 달라집니다. 이 구성에서 네 꼭짓점을 학습한 결과는 이중선형 보간과 거의 같습니다. 예를 들어 A에서만 강하게 울리는 타격은 위쪽 가운데에서 약 절반 세기로 울립니다.

`/beatmap/inputs`로 3개 값이 6460번 포트에 들어가고 `/beatmap/prediction`으로 49개 값이 12014번 포트에 돌아옵니다. **Train & play**는 이 전용 Wekinator 프로젝트의 학습 예시를 네 모서리 패턴으로 갱신합니다. 킥·스네어·하이햇은 Processing 스케치 안에서 합성합니다.
