# GoSure Telematics Pvt Ltd — BR05 Commands

> Extracted from the provided `BR05 COMMANDS.pdf`.  
> **Note:** The source PDF is scanned/image-based, so some characters were ambiguous in OCR. The command structure and terminology below follow the extracted source text.

## 1. Quick Start

### Set domain/IP & Port

```text
SERVER,1,domain,port#
```

Example:

```text
SERVER,1,gateway.test.com,11139#
```

```text
SERVER,0,IP,port#
```

Example:

```text
SERVER,0,120.234.211.110,11139#
```

Query current setting:

```text
SERVER#
```

### Set APN

```text
APN,apn name#
```

Example:

```text
APN,cmnet#
```

APN with username and password:

```text
APN,apn name,username,password#
```

Example:

```text
APN,cmnet,test,test#
```

Query current setting:

```text
APN#
```

**Note:** After configuring the APN, the device will restart after 10 seconds.

After completing the settings of server and APN, the device will connect to the platform.

---

## 2. General Parameter Configuration

### Upload interval

```text
TIMER,T1,T2#
```

Example:

```text
TIMER,10,300#
```

- T1 = 0, 5~18000 seconds: ACC ON upload interval.
- T2 = 0, 5~18000 seconds: ACC OFF upload interval.

Query:

```text
TIMER#
```

### Upload distance

**If it is TIMER mode, DISTANCE mode will auto OFF.**

```text
DISTANCE,D#
```

Example:

```text
DISTANCE,300#
```

- D = 0, 50~10000 meters
- Unit: meter
- Default: 300 meters

Query:

```text
DISTANCE#
```

---

## 3. Heartbeat Interval

```text
HBT,T1,T2#
```

Example:

```text
HBT,6,8#
```

- T1: ACC ON heartbeat packet upload interval, range 1–10 minutes.
- T2: ACC OFF heartbeat packet upload interval, range 1–10 minutes.

Query:

```text
HBT#
```

---

## 4. Angle Report

```text
ANGLEREP,X,A,B#
```

Example:

```text
ANGLEREP,ON,20,3#
```

- X = ON/OFF
- Default: ON
- A = 5~180 degrees deflection angle
- Default: 20 degrees
- B = 2~5 seconds detection time
- Default: 3 seconds

Turn off:

```text
ANGLEREP,OFF#
```

Query:

```text
ANGLEREP#
```

---

## 5. SOS Number Settings

### Add SOS numbers

```text
SOS,A,Number1,Number2,Number3#
```

### Delete SOS number by serial number

```text
SOS,D,N1,N2,N3#
```

### Delete SOS number by number

```text
SOS,D,SOS number#
```

### Modify SOS number

```text
SOS,U,N,SOS number#
```

Where:

```text
N = Serial number 1/2/3
```

---

## 6. Center Number Setting

### Add center number

```text
CENTER,A,center number#
```

### Delete center number

```text
CENTER,D#
```

---

## 7. External Power Voltage Reporting

**Requires hardware support**

```text
ADT,SW,T#
```

- SW = ON or OFF
- T = Upload interval, range 5–3600 seconds
- Default = OFF, 600 seconds

Query:

```text
ADT#
```

---

# 8. Alarm Parameter Configuration

## Over Speeding Alarm

```text
SPEED,A,B,C,M#
```

- A = ON/OFF — overspeed alarm
- Default: ON
- B = 5~600 seconds — detection time
- Default: 20 seconds
- C = 1~255 km/h — overspeed threshold
- Default: 50 km/h
- M = 0/1 — alarm reporting method
  - 0 = GPRS only
  - 1 = SMS + GPRS

Query:

```text
SPEED#
```

---

## Vibration Alarm

```text
SENALM,A,M#
```

- A = ON/OFF
- Default: ON
- M = 0~3 — alarm reporting method

```text
0 = GPRS only
1 = SMS + GPRS
2 = GPRS + SMS + CALL
3 = GPRS + CALL
```

Query:

```text
SENALM#
```

---

## Power Failure Alarm

```text
POWERALM,A,M,T1,T2,T3#
```

- A = ON/OFF
- Default: ON
- M = 0~3

```text
0 = GPRS only
1 = SMS + GPRS
2 = GPRS + SMS + CALL
3 = GPRS + CALL
```

- T1 = 2~3600 seconds
  - Power failure detection time
  - Default: 10 seconds
- T2 = 1~3600 seconds
  - Minimum charging time
  - Default: 1 second
- T3 = 0~3600 seconds
  - ACC ON to OFF transition prohibition alarm time
  - Default: 0 seconds

Query:

```text
POWERALM#
```

---

## Low Battery Alarm

```text
BATALM,A,M#
```

- A = ON/OFF
- Default: ON
- M = 0~1

```text
0 = GPRS only
1 = SMS + GPRS
```

Query:

```text
BATALM#
```

---

## External Power Low Battery Alarm

```text
EXBATALM,SW,M,V1,V2,T#
```

- SW = ON/OFF
- Default: OFF
- V1 = low battery alarm threshold
  - Range: 10–1000
- V2 = threshold voltage to release prohibition alarm
  - Range: 10–1000
- M = 0~1

```text
0 = GPRS only
1 = SMS + GPRS
```

- T = detection time
- Range: 1–300 seconds

Default:

```text
EXBATALM,OFF,0,128,138,10#
```

Query:

```text
EXBATALM#
```

---

## External Low Power Protection Voltage Reminder

**Requires hardware support**

```text
EXBATCUT,SW,M,V1,V2,T#
```

- SW = ON/OFF
- M = alarm reporting method

```text
0 = GPRS only
1 = SMS + GPRS
```

- V1 = low battery alarm threshold
- Range: 10–360
- V2 = threshold voltage to release prohibition alarm
- Range: 10–360
- T = detection time
- Range: 1–300 seconds

Default:

```text
EXBATCUT,OFF,0,115,120,10#
```

Query:

```text
EXBATCUT#
```

---

## Low Battery Protection — Flight Mode

```text
FLYCUT,SW#
```

- SW = ON/OFF

Example:

```text
FLYCUT,ON#
```

Query:

```text
FLYCUT#
```

---

## Displacement Alarm

```text
MOVING,A,R,M#
```

- A = ON/OFF
- Default: OFF
- R = displacement radius
- Range: 100~1000 meters
- M = alarm reporting method

```text
0 = GPRS only
1 = SMS + GPRS
2 = GPRS + SMS + CALL
3 = GPRS + CALL
```

Query:

```text
MOVING#
```

---

## Violent Driving Alarm

```text
SPEEDCHECK,A,M,T,AV1,AV2#
```

- A = ON/OFF
- Default: OFF
- M = 0~1 alarm type
- Default: 0 GPRS only
- T = 1~30 seconds detection time
- Default: 4 seconds
- AV1 = 10~300 km/h acceleration threshold
- Default: 30 km/h
- AV2 = 10~300 km/h deceleration threshold
- Default: 50 km/h

Query:

```text
SPEEDCHECK#
```

---

## Sharp Turn Alarm

```text
SWERVE,A,M,AC,V,T#
```

- A = ON/OFF
- Default: OFF
- M = 0~1 alarm type
- Default: 0 GPRS only
- AC = 10~180 degrees heading angle change threshold
- Default: 30 degrees
- V = 10~200 km/h speed threshold
- Default: 60 km/h
- T = 1~30 seconds sharp turn detection time
- Default: 3 seconds

Query:

```text
SWERVE#
```

---

## Collision Alarm

**Requires hardware support**

```text
COLLIDE,A,M,N#
```

- A = ON/OFF
- Default: OFF
- M = 0~1 reporting mode
- Default: 0 GPRS
- N = 10~1024 collision level
- Default: 800

Query:

```text
COLLIDE#
```

---

## Rollover Alarm

**Requires hardware support**

```text
ROLLOVER,SW,M,N,T#
```

- SW = ON/OFF
- M = alarm reporting method

```text
0 = GPRS only
1 = SMS + GPRS
```

- N = average value change threshold
- Range: 1~40
- Unit: 0.1g
- T = continuous sampling time of three-axis data after a valid collision alarm
- Range: 1~9 seconds

Query:

```text
ROLLOVER#
```

---

## Removal Alarm

**Requires hardware support**

```text
PULLALM,A,M,T,A#
```

- A = ON/OFF
- Default: OFF
- M = alarm reporting method

```text
0 = GPRS only
1 = SMS + GPRS
2 = GPRS + SMS + CALL
3 = GPRS + CALL
```

- Default: 0
- T = power outage detection time
- Range: 2–60 seconds
- Default: 20 seconds
- A = 1–100
- Use 10 times the A value as acceleration judgment value
- Default = 30

Query:

```text
PULLALM#
```

---

## Falling Off Alarm

**Requires hardware support**

```text
TURNOVER,A,M,N,T1,T2,T3#
```

- A = ON/OFF
- Default: OFF

M = alarm reporting method:

```text
0 = GPRS only
1 = SMS + GPRS
2 = GPRS + SMS + CALL
3 = GPRS + CALL
```

Default:

```text
M = 0
```

N = calibration value three-axis comparison phase difference intensity

- Range: 1~20
- Unit: 0.1g
- Default: 7

T1 = time to collect three-axis calibration values

- Range: 3~60 seconds
- Default: 30 seconds

T2 = duration of trigger condition

- Range: 3~90 seconds
- Default: 5 seconds
- T1 + 30 must be greater than T2

T3 = silent time

- Range: 1~60 minutes
- Default: 10 minutes

Query:

```text
TURNOVER#
```

---

## ACC Alarm

**Requires hardware support**

```text
ACCALM,SW,M,T,N#
```

- SW = ON/OFF
- M = alarm reporting method

```text
0 = GPRS only
1 = SMS + GPRS
```

- T = detection time
- Range: 5–60 seconds

N = Report type:

```text
0 = alarm for ACC status change
1 = alarm for ACC turning OFF
2 = alarm for ACC turning ON
```

Query:

```text
ACCALM#
```

---

## SOS Alarm

**Requires hardware support**

```text
SOSALM,ON,M,T#
```

- ON = turn on SOS alarm
- Default: ON
- M = alarm reporting method

```text
0 = GPRS only
1 = SMS + GPRS
2 = GPRS + SMS + telephone
3 = GPRS + CALL
```

- T = alarm trigger delay
- Range: 100~10000 ms
- Default: 3000 ms

**Default alarm mode: reporting mode is 2.**

Query:

```text
SOSALM#
```

---

# 9. Query Commands

### Query device version

```text
VERSION
```

### Query device status

```text
STATUS
```

### Device self-check

```text
CHECK
```

### Longitude and latitude position query

```text
WHERE
```

### Location link query

```text
URL
```

### ICCID query

```text
ICCID
```

### TMST query

```text
TMST
```

---

# 10. Other Commands

## Restore factory settings

```text
FACTORY
```

## Restart command

```text
RESET,A#
```

Where:

```text
A = 1–60 seconds
```

The terminal restarts after A seconds.

---

# 11. Password Switch

## Turn ON command password function

```text
PWDSW,ON#
```

Default:

```text
OFF
```

## Turn OFF command password function

```text
PWDSW,P,OFF#
```

Where:

```text
P = Password
```

After turning password protection on, SMS commands for setting and querying must include the password.

Example:

```text
SERVER,666666#
```

---

# 12. Change Password

```text
PASSWORD,P1,P2#
```

- P1 = old password
- P2 = new password

Factory default password:

```text
666666
```

Example:

```text
PASSWORD,666666,888888#
```

Response:

```text
PASSWORD set OK!
```
