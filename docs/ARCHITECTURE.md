# Vlettenrally App - Architecture Document

## Executive Summary

This document outlines the architecture for the Vlettenrally App, a comprehensive responsive web application for managing and participating in Dutch scouting boat rally events. The app serves two primary user groups: participants (teams on boats) and administrators (rally organizers). The application uses WebSockets for real-time communication and is designed to support up to 500 concurrent participants.

## Requirements Analysis

Based on GitHub issues #1-11, the app requires:

### Participant Features
1. **Check-in System** (Issue #1)
   - Game selection
   - Boat number registration
   - Emergency contact registration
   - Participant name registration
   - Rival team selection
   - Game instructions display

2. **Messaging System** (Issues #2-5)
   - Countdown notifications (30s to 60min intervals)
   - Achievement notifications (points, checkpoints)
   - Rival team updates
   - Statistics notifications (speed records, rankings)
   - General messages from rally desk
   - Weather information

### Administrative Features
3. **Game Configuration** (Issue #6)
   - Create/import rally games
   - Define game start/end times
   - Configure starting area times
   - Configure checkpoint opening times
   - Manage game settings

4. **Team Management** (Issue #7)
   - Import/create teams
   - Assign boat numbers
   - Manage team details
   - Configure rivals
   - Track device check-ins

5. **Message Management** (Issue #9)
   - Create message templates
   - Manage message variants
   - Send manual messages
   - Target specific teams or groups

6. **Automation System** (Issue #10)
   - Rule-based automated notifications
   - Trigger conditions (points, checkpoints, speed, rankings)
   - Time-based triggers
   - Countdown automation
   - Rate limiting

7. **System Configuration** (Issue #8)
   - API settings
   - System parameters

8. **Admin Notifications** (Issue #11)
   - Speed limit alerts
   - Configurable thresholds

9. **Location Data Polling** (Issue #13)
   - Periodic REST API data retrieval
   - Location information extraction and processing
   - Location data storage for analysis
   - Event triggering on new data
   - Error and connection handling
   - Configurable endpoint URL and polling frequency

## Architecture

### Containerized Web Application with External Cache

```
┌─────────────────────────────────────────────────────────────────┐
│                    Responsive Web Frontend                       │
│  ┌──────────────────────┐          ┌──────────────────────────┐ │
│  │  Participant UI      │          │  Admin UI                │ │
│  │  - React + TypeScript│          │  - React + TypeScript    │ │
│  │  - Responsive Design │          │  - Admin Dashboard       │ │
│  │  - Progressive Web   │          │  - Configuration         │ │
│  │    App (PWA)         │          │  - Management            │ │
│  └──────────────────────┘          └──────────────────────────┘ │
│           WebSocket Connection ←──────────────────→              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              Backend Containers (Scalable)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  Container 1 │  │  Container 2 │  │  Container N         │ │
│  │  ┌────────────────────────────────────────────────────┐  │ │
│  │  │  Quarkus Application (Java)                        │  │ │
│  │  │  ┌──────────────────────────────────────────────┐  │  │ │
│  │  │  │  API Layer (REST + WebSocket)                │  │  │ │
│  │  │  │  - JAX-RS (RESTEasy) for REST endpoints      │  │  │ │
│  │  │  │  - Quarkus WebSockets for real-time          │  │  │ │
│  │  │  │  - SmallRye JWT for authentication           │  │  │ │
│  │  │  └──────────────────────────────────────────────┘  │  │ │
│  │  │  ┌──────────────────────────────────────────────┐  │  │ │
│  │  │  │  Business Logic (CDI Beans)                  │  │  │ │
│  │  │  │  - GameService                               │  │  │ │
│  │  │  │  - TeamService                               │  │  │ │
│  │  │  │  - MessageService (Broadcasting)             │  │  │ │
│  │  │  │  - AutomationEngine (Quartz Scheduler)       │  │  │ │
│  │  │  │  - AnalyticsService                          │  │  │ │
│  │  │  │  - NotificationService (Rules Engine)        │  │  │ │
│  │  │  │  - LocationPollingService (REST Client)      │  │  │ │
│  │  │  └──────────────────────────────────────────────┘  │  │ │
│  │  └────────────────────────────────────────────────────┘  │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  External Data Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  PostgreSQL  │  │ Redis Cache  │  │  File Storage        │ │
│  │  (External)  │  │  (External)  │  │  - S3 Compatible     │ │
│  │  - Games     │  │  - Sessions  │  │  - MinIO/S3          │ │
│  │  - Teams     │  │  - Cache     │  │  - Documents         │ │
│  │  - Messages  │  │  - Pub/Sub   │  │  - Exports           │ │
│  │  - Audit Log │  │  - WebSocket │  │                      │ │
│  │              │  │    State     │  │                      │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**Pros:**
- No vendor lock-in - full control over infrastructure
- Horizontal scalability - add more containers as needed
- Stateless containers - can scale up/down freely
- External Redis enables WebSocket clustering
- Container orchestration ready (Kubernetes, Docker Swarm)
- Quarkus fast startup and low memory footprint
- Native compilation option with GraalVM for even better performance
- Progressive Web App provides app-like experience

**Cons:**
- Slightly more complex than single server
- Requires container orchestration for multiple instances
- Need external Redis for session sharing

## Recommended Architecture: Containerized Web Application with Java Quarkus

### Technology Stack

#### Frontend
- **Web Application**: React + TypeScript + Vite
  - Single responsive application for both participants and admins
  - Progressive Web App (PWA) for app-like experience on mobile
  - Works offline with service workers
  - Material-UI or Tailwind CSS for responsive design
  - Mobile-first design approach
  
#### Backend
- **Runtime**: Java 21 (LTS) with Quarkus Framework
  - Supersonic Subatomic Java - fast startup, low memory footprint
  - Native compilation with GraalVM (optional)
  - Reactive and imperative programming models
  - Developer joy with live reload
  
- **REST API**: RESTEasy Reactive (JAX-RS)
  - Standard Java API for RESTful web services
  - Reactive programming support
  - OpenAPI/Swagger integration
  - JSON-B or Jackson for JSON serialization
  
- **Real-time Communication**: Quarkus WebSockets
  - JSR 356 WebSocket API
  - Server-sent events (SSE) support
  - Integration with Redis for clustering
  - Automatic reconnection handling
  
- **Database Access**: Hibernate ORM with Panache
  - Repository pattern with minimal boilerplate
  - Type-safe queries
  - Transaction management
  - Database migrations with Flyway or Liquibase
  
- **Scheduling**: Quartz Scheduler
  - Cron-based job scheduling
  - Persistent job store
  - Cluster support
  - Automation engine execution

- **Authentication**: SmallRye JWT
  - JWT token generation and validation
  - Role-based access control (RBAC)
  - MicroProfile JWT standard
  - Integration with external identity providers

- **Location Polling**: Quarkus REST Client + Quartz
  - MicroProfile REST Client for external API calls
  - Quartz scheduler for periodic polling
  - Configurable polling intervals per endpoint
  - CDI events for internal event distribution
  - Automatic retry and error handling
  - Location data storage in PostgreSQL

#### Infrastructure
- **Database**: PostgreSQL
  - Open-source, battle-tested
  - Excellent for relational data
  - JSONB for flexible data
  - Can host anywhere
  
- **Cache/Session Store**: Redis (External)
  - Fast in-memory storage
  - Pub/Sub for WebSocket clustering
  - Session management across containers
  - Rate limiting and distributed locks
  - Enables horizontal scaling
  
- **Container Runtime**: Docker
  - Containerized Quarkus application
  - Multi-stage build for optimized images
  - Native image support (GraalVM)
  - Small container size (~150MB JVM, ~50MB native)
  
- **Container Orchestration**:
  - **Simple**: Docker Compose (development/small deployments)
  - **Scalable**: Kubernetes or Docker Swarm (production)
  - **Managed**: Cloud-agnostic (works on any provider)
  
- **Hosting Options**:
  - Hetzner Cloud with Kubernetes (€20-40/month)
  - DigitalOcean Kubernetes ($40-80/month)
  - Any VPS with Docker support
  - On-premises with Docker/Kubernetes
  
- **Load Balancer**: Nginx or Traefik
  - SSL/TLS termination
  - Static file serving
  - WebSocket proxying
  - Round-robin load balancing

#### Development Tools
- **Build Tool**: Gradle
  - Dependency management
  - Multi-module projects
  - Integration with Quarkus tooling
  - Kotlin DSL or Groovy DSL support
  
- **Testing**: 
  - JUnit 5 for unit tests
  - RestAssured for API testing
  - Testcontainers for integration tests
  - Playwright for E2E frontend tests
  
- **Monitoring**: 
  - Quarkus Micrometer for metrics
  - Prometheus endpoint
  - Grafana for visualization
  - Health checks and readiness probes

### Database Schema

```sql
-- Games (Rally Events)
CREATE TABLE games (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name VARCHAR(255) NOT NULL,
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP NOT NULL,
  check_in_start_time TIMESTAMP,
  start_signal_time TIMESTAMP,
  status VARCHAR(50) DEFAULT 'planned', -- planned, active, completed
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Checkpoints
CREATE TABLE checkpoints (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  game_id UUID REFERENCES games(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  opening_time TIMESTAMP NOT NULL,
  closing_time TIMESTAMP,
  points INTEGER DEFAULT 0,
  latitude DECIMAL(10, 8),
  longitude DECIMAL(11, 8),
  created_at TIMESTAMP DEFAULT NOW()
);

-- Teams
CREATE TABLE teams (
  id UUID PRIMARY KEY,
  game_id UUID NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  device_id UUID,
  checked_in BOOLEAN NOT NULL DEFAULT FALSE,
  total_points INTEGER NOT NULL DEFAULT 0,
  notes TEXT,
  external_id VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  
  CONSTRAINT fk_team_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
  CONSTRAINT fk_team_device FOREIGN KEY (device_id) REFERENCES device_registrations(id) ON DELETE SET NULL
);

-- Team participants (ElementCollection)
CREATE TABLE team_participants (
  team_id UUID NOT NULL,
  participant_name VARCHAR(255) NOT NULL,
  participant_order INTEGER NOT NULL DEFAULT 0,
  
  CONSTRAINT fk_participant_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

-- Team emergency phone numbers (ElementCollection)
CREATE TABLE team_emergency_phones (
  team_id UUID NOT NULL,
  phone_number VARCHAR(50) NOT NULL,
  phone_order INTEGER NOT NULL DEFAULT 0,
  
  CONSTRAINT fk_emergency_phone_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

-- Rivals (Many-to-Many relationship)
CREATE TABLE team_rivals (
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  rival_team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  PRIMARY KEY (team_id, rival_team_id)
);

-- Device Registrations
CREATE TABLE device_registrations (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  device_token VARCHAR(255) UNIQUE NOT NULL,
  platform VARCHAR(50), -- ios, android
  registered_at TIMESTAMP DEFAULT NOW(),
  last_active TIMESTAMP DEFAULT NOW()
);

-- Team Achievements (Points, Checkpoints)
CREATE TABLE team_achievements (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  checkpoint_id UUID REFERENCES checkpoints(id) ON DELETE CASCADE,
  points_earned INTEGER DEFAULT 0,
  achieved_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(team_id, checkpoint_id)
);

-- Speed Records
CREATE TABLE speed_records (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  speed DECIMAL(5, 2) NOT NULL, -- km/h
  latitude DECIMAL(10, 8),
  longitude DECIMAL(11, 8),
  recorded_at TIMESTAMP DEFAULT NOW()
);

-- Message Templates
CREATE TABLE message_templates (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  title VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL, -- general, countdown, checkpoint, rallydesk
  content TEXT NOT NULL,
  icon VARCHAR(255),
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Message Variants (for A/B testing or context)
CREATE TABLE message_variants (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  template_id UUID REFERENCES message_templates(id) ON DELETE CASCADE,
  variant_name VARCHAR(255),
  content TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Automations
CREATE TABLE automations (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name VARCHAR(255) NOT NULL,
  game_id UUID REFERENCES games(id) ON DELETE CASCADE,
  is_active BOOLEAN DEFAULT TRUE,
  
  -- Conditions
  date_time_start TIMESTAMP,
  date_time_end TIMESTAMP,
  active_during_game BOOLEAN DEFAULT TRUE,
  
  -- Triggers
  trigger_type VARCHAR(50), -- points, checkpoint, speed, ranking
  trigger_target VARCHAR(50), -- own_team, rival
  trigger_value JSONB, -- flexible storage for trigger parameters
  
  -- Message
  message_type VARCHAR(50), -- general, countdown, checkpoint
  message_template_id UUID REFERENCES message_templates(id),
  countdown_target VARCHAR(50), -- start_area, start_signal, checkpoint_opening
  countdown_checkpoint_id UUID REFERENCES checkpoints(id),
  countdown_intervals INTEGER[], -- [30, 60, 120, 300, 600, 1200, 1800, 3600] seconds
  
  -- Rate limiting
  max_notifications_per_interval INTEGER,
  interval_minutes INTEGER,
  
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Sent Messages (for tracking and rate limiting)
CREATE TABLE sent_messages (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  automation_id UUID REFERENCES automations(id) ON DELETE SET NULL,
  message_type VARCHAR(50),
  content TEXT NOT NULL,
  icon VARCHAR(255),
  sent_at TIMESTAMP DEFAULT NOW(),
  delivered BOOLEAN DEFAULT FALSE,
  read BOOLEAN DEFAULT FALSE
);

-- Admin Users
CREATE TABLE admin_users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email VARCHAR(255) UNIQUE NOT NULL,
  role VARCHAR(50) DEFAULT 'admin', -- admin, super_admin
  created_at TIMESTAMP DEFAULT NOW()
);

-- System Configuration
CREATE TABLE system_config (
  key VARCHAR(255) PRIMARY KEY,
  value JSONB NOT NULL,
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Admin Alerts Configuration
CREATE TABLE admin_alert_config (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  alert_type VARCHAR(50) NOT NULL, -- speed_limit
  threshold_value DECIMAL(10, 2),
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT NOW()
);

-- API Endpoint Configuration (for location polling)
CREATE TABLE api_endpoint_config (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name VARCHAR(255) NOT NULL,
  endpoint_url TEXT NOT NULL,
  polling_frequency_seconds INTEGER NOT NULL DEFAULT 60,
  is_active BOOLEAN DEFAULT TRUE,
  last_poll_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Location Data (polled from external API)
CREATE TABLE location_data (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  latitude DECIMAL(10, 8) NOT NULL,
  longitude DECIMAL(11, 8) NOT NULL,
  altitude DECIMAL(8, 2),
  speed DECIMAL(5, 2),
  heading DECIMAL(5, 2),
  accuracy DECIMAL(8, 2),
  timestamp TIMESTAMP NOT NULL,
  api_endpoint_id UUID REFERENCES api_endpoint_config(id),
  raw_data JSONB,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_teams_game_id ON teams(game_id);
CREATE INDEX idx_checkpoints_game_id ON checkpoints(game_id);
CREATE INDEX idx_team_achievements_team_id ON team_achievements(team_id);
CREATE INDEX idx_sent_messages_team_id ON sent_messages(team_id);
CREATE INDEX idx_sent_messages_sent_at ON sent_messages(sent_at);
CREATE INDEX idx_speed_records_team_id ON speed_records(team_id);
CREATE INDEX idx_automations_game_id ON automations(game_id);
CREATE INDEX idx_location_data_team_id ON location_data(team_id);
CREATE INDEX idx_location_data_timestamp ON location_data(timestamp);
CREATE INDEX idx_location_data_api_endpoint_id ON location_data(api_endpoint_id);
```

### Application Architecture Details

#### Project Structure
```
vlettenrally-app/
├── frontend/                  # React web application
│   ├── src/
│   │   ├── pages/
│   │   │   ├── participant/
│   │   │   │   ├── CheckIn.tsx
│   │   │   │   ├── Dashboard.tsx
│   │   │   │   ├── Messages.tsx
│   │   │   │   ├── Rankings.tsx
│   │   │   │   └── Rivals.tsx
│   │   │   └── admin/
│   │   │       ├── Dashboard.tsx
│   │   │       ├── Games.tsx
│   │   │       ├── Teams.tsx
│   │   │       ├── Messages.tsx
│   │   │       ├── Automations.tsx
│   │   │       └── Analytics.tsx
│   │   ├── components/
│   │   │   ├── layout/
│   │   │   ├── forms/
│   │   │   ├── CountdownTimer.tsx
│   │   │   ├── NotificationCard.tsx
│   │   │   └── RankingTable.tsx
│   │   ├── hooks/
│   │   │   ├── useWebSocket.ts
│   │   │   ├── useAuth.ts
│   │   │   ├── useGame.ts
│   │   │   └── useRealtime.ts
│   │   ├── services/
│   │   │   ├── api.ts
│   │   │   ├── websocket.ts
│   │   │   └── storage.ts
│   │   ├── store/
│   │   │   ├── authSlice.ts
│   │   │   ├── gameSlice.ts
│   │   │   └── messagesSlice.ts
│   │   └── utils/
│   │       ├── dateTime.ts
│   │       └── validators.ts
│   ├── public/
│   │   ├── manifest.json         # PWA manifest
│   │   └── service-worker.js     # Service worker for offline
│   ├── Dockerfile
│   ├── vite.config.ts
│   └── package.json
│
├── backend/                   # Quarkus application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/vlettenrally/
│   │   │   │       ├── config/
│   │   │   │       │   ├── SecurityConfig.java
│   │   │   │       │   ├── WebSocketConfig.java
│   │   │   │       │   └── RedisConfig.java
│   │   │   │       ├── domain/
│   │   │   │       │   ├── entity/
│   │   │   │       │   │   ├── Game.java
│   │   │   │       │   │   ├── Team.java
│   │   │   │       │   │   ├── Checkpoint.java
│   │   │   │       │   │   ├── Message.java
│   │   │   │       │   │   ├── Automation.java
│   │   │   │       │   │   ├── TeamAchievement.java
│   │   │   │       │   │   ├── LocationData.java
│   │   │   │       │   │   └── ApiEndpointConfig.java
│   │   │   │       │   └── repository/
│   │   │   │       │       ├── GameRepository.java
│   │   │   │       │       ├── TeamRepository.java
│   │   │   │       │       └── MessageRepository.java
│   │   │   │       ├── service/
│   │   │   │       │   ├── GameService.java
│   │   │   │       │   ├── TeamService.java
│   │   │   │       │   ├── MessageService.java
│   │   │   │       │   ├── AutomationService.java
│   │   │   │       │   ├── AnalyticsService.java
│   │   │   │       │   ├── NotificationService.java
│   │   │   │       │   └── LocationPollingService.java
│   │   │   │       ├── rest/
│   │   │   │       │   ├── GameResource.java
│   │   │   │       │   ├── TeamResource.java
│   │   │   │       │   ├── MessageResource.java
│   │   │   │       │   ├── AutomationResource.java
│   │   │   │       │   └── AnalyticsResource.java
│   │   │   │       ├── websocket/
│   │   │   │       │   ├── GameWebSocket.java
│   │   │   │       │   ├── MessageBroadcaster.java
│   │   │   │       │   └── WebSocketSessionManager.java
│   │   │   │       ├── scheduler/
│   │   │   │       │   ├── AutomationJob.java
│   │   │   │       │   ├── CountdownJob.java
│   │   │   │       │   ├── AnalyticsJob.java
│   │   │   │       │   └── LocationPollingJob.java
│   │   │   │       ├── dto/
│   │   │   │       │   ├── request/
│   │   │   │       │   └── response/
│   │   │   │       └── security/
│   │   │   │           ├── JwtService.java
│   │   │   │           └── SecurityIdentity.java
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       ├── application-dev.properties
│   │   │       └── db/migration/
│   │   │           └── V1__initial_schema.sql
│   │   └── test/
│   │       └── java/
│   │           └── com/vlettenrally/
│   │               ├── GameResourceTest.java
│   │               └── TeamServiceTest.java
│   ├── Dockerfile.jvm
│   ├── Dockerfile.native
│   ├── build.gradle                  # Gradle build file
│   ├── settings.gradle               # Gradle settings
│   └── README.md
│
├── docker-compose.yml            # Local development setup
├── docker-compose.prod.yml       # Production setup
├── kubernetes/                   # Kubernetes manifests (optional)
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   └── configmap.yaml
└── README.md
```

#### WebSocket Communication Architecture
```java
// WebSocket Endpoint
@ServerEndpoint("/ws/game/{gameId}")
@ApplicationScoped
public class GameWebSocket {
    
    @Inject
    MessageBroadcaster broadcaster;
    
    @Inject
    RedisClient redisClient;
    
    @OnOpen
    public void onOpen(Session session, @PathParam("gameId") String gameId) {
        // Join game room
        session.getUserProperties().put("gameId", gameId);
        broadcaster.addSession(gameId, session);
    }
    
    @OnMessage
    public void onMessage(String message, Session session) {
        // Handle incoming messages
        String gameId = (String) session.getUserProperties().get("gameId");
        // Process and broadcast via Redis pub/sub for clustering
        redisClient.publish("game:" + gameId, message);
    }
    
    @OnClose
    public void onClose(Session session) {
        String gameId = (String) session.getUserProperties().get("gameId");
        broadcaster.removeSession(gameId, session);
    }
}

// Message Broadcaster with Redis for Clustering
@ApplicationScoped
public class MessageBroadcaster {
    
    @Inject
    RedisClient redisClient;
    
    private final Map<String, Set<Session>> gameSessions = new ConcurrentHashMap<>();
    
    public void broadcastToGame(String gameId, Message message) {
        // Broadcast locally
        Set<Session> sessions = gameSessions.get(gameId);
        if (sessions != null) {
            String json = Json.encode(message);
            sessions.forEach(session -> session.getAsyncRemote().sendText(json));
        }
        
        // Publish to Redis for other instances
        redisClient.publish("broadcast:game:" + gameId, Json.encode(message));
    }
    
    @ConsumeEvent("broadcast:game:*")
    public void onRedisBroadcast(String channel, String message) {
        String gameId = extractGameId(channel);
        // Broadcast to local sessions
        broadcastToLocalSessions(gameId, message);
    }
}

// Event Types
public enum WebSocketEventType {
    MESSAGE_NEW,
    COUNTDOWN_UPDATE,
    TEAM_ACHIEVEMENT,
    RIVAL_UPDATE,
    RANKING_UPDATE,
    GAME_STATUS,
    ADMIN_ALERT
}
```

### Key Features Implementation

#### 1. Real-time Communication via WebSockets
- Socket.IO for bidirectional real-time communication
- Automatic reconnection handling
- Room-based broadcasting for efficiency
- Redis adapter for horizontal scaling (if needed)
- Fallback to long-polling for restrictive networks

#### 2. Progressive Web App (PWA)
- Service worker for offline functionality
- App manifest for "Add to Home Screen"
- Cache-first strategy for static assets
- Network-first for API calls with offline queue
- Background sync for pending actions

#### 3. Automation Engine
- Node-cron for scheduled jobs (runs every minute)
- Evaluates automation rules against current game state
- Rate limiting using database queries
- Event-driven triggers on achievements
- Configurable through admin UI

#### 4. Authentication & Authorization
- JWT tokens stored in httpOnly cookies
- Role-based access control (RBAC)
  - Participant: Read own team data, read rival data
  - Admin: Full access to game management
- Session management with Redis
- CSRF protection

#### 5. Responsive Design
- Mobile-first approach
- Breakpoints: Mobile (< 768px), Tablet (768-1024px), Desktop (> 1024px)
- Touch-friendly UI elements
- Different layouts for participant vs admin views
- Works on phones, tablets, and desktops

### Deployment Strategy

#### Development Environment
```bash
# Start all services with Docker Compose
docker-compose up -d

# Services:
# - PostgreSQL on port 5432
# - Redis on port 6379
# - Backend (Quarkus) on port 8080 (with live reload)
# - Frontend on port 5173 (Vite dev server)

# Quarkus dev mode for live reload
cd backend
./gradlew quarkusDev
```

#### Production Deployment - Containerized with External Cache

**Architecture Overview:**
```
                    ┌─────────────────┐
                    │  Load Balancer  │
                    │   (Nginx/       │
                    │    Traefik)     │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
         ┌────▼────┐    ┌────▼────┐   ┌────▼────┐
         │Container│    │Container│   │Container│
         │    1    │    │    2    │   │    N    │
         │ Quarkus │    │ Quarkus │   │ Quarkus │
         └────┬────┘    └────┬────┘   └────┬────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
         ┌────▼────┐    ┌────▼────┐   ┌────▼────┐
         │PostgreSQL│   │  Redis  │   │ MinIO/  │
         │(External)│   │(External)│   │   S3    │
         └──────────┘    └──────────┘   └─────────┘
```

**Container Build:**

JVM Mode (Fast build, larger image ~150MB):
```dockerfile
# Dockerfile.jvm
FROM registry.access.redhat.com/ubi8/openjdk-21:latest

ENV LANGUAGE='en_US:en'

# Copy built jar
COPY --chown=185 build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 build/quarkus-app/*.jar /deployments/
COPY --chown=185 build/quarkus-app/app/ /deployments/app/
COPY --chown=185 build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
```

Native Mode (Slower build, tiny image ~50MB, faster startup):
```dockerfile
# Dockerfile.native
FROM quay.io/quarkus/quarkus-micro-image:2.0

COPY --chown=1001:root build/*-runner /application

EXPOSE 8080
USER 1001

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

**Docker Compose Production Setup:**
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: vlettenrally
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    networks:
      - backend
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    networks:
      - backend
    restart: unless-stopped

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile.jvm
    environment:
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/vlettenrally
      QUARKUS_DATASOURCE_USERNAME: ${DB_USER}
      QUARKUS_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      QUARKUS_REDIS_HOSTS: redis://redis:6379
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      - postgres
      - redis
    networks:
      - backend
      - frontend
    restart: unless-stopped
    deploy:
      replicas: 2                # Run 2 instances
      resources:
        limits:
          cpus: '1'
          memory: 512M

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    networks:
      - frontend
    restart: unless-stopped

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
    depends_on:
      - frontend
      - backend
    networks:
      - frontend
    restart: unless-stopped

volumes:
  postgres_data:
  redis_data:

networks:
  backend:
  frontend:
```

**Kubernetes Deployment (For Scalable Production):**

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vlettenrally-backend
spec:
  replicas: 3                    # Start with 3 instances
  selector:
    matchLabels:
      app: vlettenrally-backend
  template:
    metadata:
      labels:
        app: vlettenrally-backend
    spec:
      containers:
      - name: backend
        image: vlettenrally/backend:latest
        ports:
        - containerPort: 8080
        env:
        - name: QUARKUS_DATASOURCE_JDBC_URL
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: database.url
        - name: QUARKUS_REDIS_HOSTS
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: redis.url
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 30
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: vlettenrally-backend
spec:
  selector:
    app: vlettenrally-backend
  ports:
  - port: 8080
    targetPort: 8080
  type: ClusterIP
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: vlettenrally-backend-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: vlettenrally-backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

#### Deployment Options

**Option A: Docker Compose on VPS (Simple, 1-3 containers)**
- Single VPS with Docker Compose
- Hetzner CPX21 (3 vCPU, 4GB RAM): €10/month
- Manual scaling by adjusting replicas
- Good for 500 concurrent users
- Setup time: 1-2 hours

**Option B: Kubernetes Cluster (Advanced, auto-scaling)**
- Managed Kubernetes (Hetzner, DigitalOcean)
- Auto-scaling based on CPU/memory
- Rolling updates with zero downtime
- Handles 500-5000+ users
- Setup time: 4-8 hours

**Option C: Cloud-Agnostic Container Platform**
- Deploy to any cloud provider
- Kubernetes or Docker Swarm
- Full portability
- No vendor lock-in

#### Deployment Phases

**Phase 1: MVP (3-4 months)**
- Basic check-in functionality
- Manual message sending from admin
- Simple countdown notifications
- Team and game management
- Real-time message delivery via WebSocket
- Deploy: Docker Compose with 1-2 containers

**Phase 2: Automation (2-3 months)**
- Full automation system with Quartz
- Advanced trigger rules
- Statistics and rankings
- Rival tracking and notifications
- Speed monitoring and alerts
- Location data polling from external APIs
- Event-driven notifications based on location data
- Deploy: Docker Compose with 2-3 containers or Kubernetes

**Phase 3: Enhancement (Ongoing)**
- Weather API integration
- Advanced analytics and reporting
- Export functionality (CSV, PDF)
- Historical data visualization
- Performance optimization
- Deploy: Kubernetes with auto-scaling

### Scalability Considerations

**Current Design with External Cache (500+ concurrent users)**
- Container-based deployment enables horizontal scaling
- External Redis enables WebSocket clustering across containers
- Load balancer distributes traffic across container instances
- Stateless containers can be added/removed dynamically
- PostgreSQL connection pooling handles concurrent requests
- Quarkus low memory footprint allows more containers per server

**Scaling Strategy:**

1. **Start Small (0-500 users)**:
   - 2 backend containers
   - Single VPS (4 vCPU, 8GB RAM)
   - External Redis for session sharing
   - Cost: ~€10-15/month

2. **Medium Scale (500-2000 users)**:
   - 3-5 backend containers
   - Larger VPS or multiple VPS
   - Managed Redis cluster
   - Load balancer (Nginx/Traefik)
   - Cost: ~€30-50/month

3. **Large Scale (2000+ users)**:
   - Kubernetes cluster with auto-scaling (2-10 pods)
   - Managed PostgreSQL with read replicas
   - Redis cluster for high availability
   - CDN for static assets
   - Cost: ~€100-200/month

**Performance Characteristics:**

- **Quarkus JVM**: ~50MB memory per instance, 200ms startup
- **Quarkus Native**: ~20MB memory per instance, 10ms startup
- **WebSocket**: 10,000+ concurrent connections per instance
- **Throughput**: 5,000+ requests/second per instance
- **Database**: 10,000+ queries/second with proper indexing

**Monitoring & Auto-scaling:**
- Prometheus metrics exposed by Quarkus
- Grafana dashboards for visualization
- Kubernetes HPA (Horizontal Pod Autoscaler) based on CPU/memory
- Alert manager for threshold notifications
- Health checks ensure only healthy containers receive traffic

### Cost Estimation (Annual)

**Deployment 1: Docker Compose on Single VPS (Recommended for Start)**
- VPS (Hetzner CPX21): €10/month = €120/year
- Domain name: €10/year
- SSL: Free (Let's Encrypt)
- Backups: €5/month = €60/year
- **Total: ~€190/year (~$210 USD)**
- **Capacity**: 500-1000 concurrent users

**Deployment 2: Docker Compose with Managed Services**
- VPS (smaller): €7/month = €84/year
- Managed PostgreSQL: €15/month = €180/year
- Managed Redis: €10/month = €120/year
- Domain & SSL: €10/year
- **Total: ~€394/year (~$430 USD)**
- **Capacity**: 1000-2000 concurrent users
- **Benefits**: Automated backups, updates, high availability

**Deployment 3: Kubernetes Cluster (For High Scale)**
- Managed Kubernetes (Hetzner): €30/month = €360/year
- Managed PostgreSQL: €20/month = €240/year
- Managed Redis: €15/month = €180/year
- Domain & SSL: €10/year
- **Total: ~€790/year (~$870 USD)**
- **Capacity**: 2000-5000+ concurrent users
- **Benefits**: Auto-scaling, zero-downtime updates, high availability

**Cost per User:**
- Deployment 1: €0.19-0.38/user/year
- Deployment 2: €0.20-0.39/user/year
- Deployment 3: €0.16-0.40/user/year

**Comparison:**
- Docker Compose: Simplest, lowest cost, manual scaling
- Kubernetes: More complex, auto-scaling, enterprise-grade

### Architecture Benefits

1. **Java Quarkus Benefits**:
   - Fast startup time (~200ms JVM, ~10ms native)
   - Low memory footprint (~50MB JVM, ~20MB native)
   - Developer-friendly with live reload
   - Enterprise-grade with strong typing
   - Excellent WebSocket support
   - Built-in metrics and health checks

2. **Container Benefits**:
   - Easy horizontal scaling
   - Consistent across environments
   - Simple deployment and rollback
   - Resource isolation
   - Works with any orchestrator

3. **External Cache (Redis)**:
   - Enables multi-container deployments
   - Session sharing across instances
   - WebSocket clustering
   - Distributed locking
   - Pub/Sub for real-time events

4. **No Vendor Lock-in**:
   - Open-source stack (Quarkus, PostgreSQL, Redis)
   - Deploy anywhere (VPS, cloud, on-premises)
   - Standard Docker containers
   - Can migrate to any provider

5. **Cost-Effective**:
   - ~€190/year for 500 users
   - Scales to 2000+ users for ~€400/year
   - Java efficiency reduces resource needs

6. **Production-Ready**:
   - Battle-tested Java ecosystem
   - Excellent monitoring and debugging tools
   - Strong community support
   - Enterprise adoption

### Migration Path

If the application grows beyond expectations:
1. **Phase 1** (Current): 1-2 containers on single VPS
2. **Phase 2** (If > 1000 users): Multiple containers with load balancer
3. **Phase 3** (If > 2000 users): Kubernetes with auto-scaling
4. **Phase 4** (If > 5000 users): Microservices split (already modular)

## Conclusion

**Architecture: Containerized Web Application with Java Quarkus**

This architecture provides:
1. **Java Quarkus backend** - Fast, efficient, enterprise-grade
2. **Container deployment** - Easy scaling with Docker
3. **External Redis cache** - Enables horizontal scaling across containers
4. **No vendor lock-in** - Open-source stack, deploy anywhere
5. **WebSocket real-time** - Quarkus WebSocket API for bidirectional communication
6. **Responsive web app** - PWA works on all devices without app stores
7. **Cost-effective** - ~€190/year for 500 users, scales to €400/year for 2000+ users
8. **Production-ready** - Battle-tested Java ecosystem with excellent tooling
9. **Location polling** - Scheduled REST API polling for location data with event triggering

The containerized Java Quarkus application is ideal for the Vlettenrally app because:
- Container deployment enables easy horizontal scaling
- External Redis allows multiple backend instances to share WebSocket state
- Quarkus provides fast startup and low memory footprint
- Java ecosystem offers enterprise-grade reliability and tooling
- 500 concurrent users start with 2 containers, can scale to 10+ as needed
- No vendor lock-in - standard Docker containers work anywhere
- Progressive Web App provides app-like experience on all devices
- Open-source stack ensures long-term viability and cost control
- Quartz scheduler enables periodic location polling with configurable intervals
- REST client support for external API integration with error handling

## Next Steps

1. **Set up development environment**
   - Install Java 21 (LTS) and Gradle
   - Initialize Quarkus project with extensions
   - Set up Docker Compose for local development
   - Configure IDE (IntelliJ IDEA recommended)

2. **Create database schema**
   - Set up Flyway or Liquibase
   - Define schema based on requirements
   - Create initial migrations
   - Configure Hibernate ORM with Panache

3. **Develop backend API**
   - Set up Quarkus project structure
   - Implement authentication (SmallRye JWT)
   - Create REST endpoints with JAX-RS
   - Integrate WebSocket support
   - Configure Redis for caching

4. **Build frontend application**
   - React + TypeScript + Vite
   - Responsive layouts (mobile-first)
   - WebSocket client integration
   - PWA configuration

5. **Implement core features**
   - Check-in system
   - Message broadcasting via WebSocket
   - Countdown timers with Quartz
   - Game & team management

6. **Containerize application**
   - Create Dockerfile for Quarkus (JVM mode)
   - Optional: Create native image Dockerfile
   - Set up Docker Compose for production
   - Configure environment variables

7. **Deploy to production**
   - Provision VPS or Kubernetes cluster
   - Deploy PostgreSQL and Redis
   - Deploy backend containers
   - Configure Nginx/Traefik load balancer
   - Set up SSL certificates

8. **Monitor and iterate**
   - Configure Prometheus metrics
   - Set up Grafana dashboards
   - Gather user feedback
   - Implement automation system
   - Add analytics features

## Appendix: Technology Alternatives

### Backend Framework
- **Quarkus**: Recommended - Fast, efficient, modern Java, great for containers
- **Spring Boot**: Alternative - More mature, larger ecosystem, heavier
- **Micronaut**: Alternative - Similar to Quarkus, good performance
- **Vert.x**: Alternative - Reactive, lower-level, more flexibility needed

### Frontend Framework
- **React + TypeScript**: Recommended - Popular, great ecosystem, TypeScript support
- **Vue.js**: Alternative - Simpler learning curve, good for small teams
- **Angular**: Alternative - Full-featured, more opinionated, TypeScript-first

### Database
- **PostgreSQL**: Recommended - Relational, JSONB for flexibility, open-source
- **MySQL/MariaDB**: Alternative - Similar features, slightly different syntax
- **MongoDB**: Not recommended - Overkill for structured data

### WebSocket Library
- **Quarkus WebSockets**: Recommended - Built-in, JSR 356 standard, easy clustering
- **Vert.x**: Alternative - High performance, more complex
- **Spring WebSocket**: Alternative - If using Spring Boot

### ORM/Database Access
- **Hibernate with Panache**: Recommended - Repository pattern, minimal boilerplate
- **jOOQ**: Alternative - Type-safe SQL, more control
- **JDBC Template**: Alternative - Lower-level, more verbose

### Container Orchestration
- **Docker Compose**: Recommended for simple deployments (1-5 containers)
- **Kubernetes**: Recommended for production scale (auto-scaling, HA)
- **Docker Swarm**: Alternative - Simpler than K8s, less features
- **Nomad**: Alternative - Lightweight, good for small teams

### Hosting Provider (No Vendor Lock-in)
- **Hetzner Cloud**: Recommended - Best price/performance, EU-based, €10-40/month
- **DigitalOcean**: Alternative - Good docs, slightly more expensive, $20-80/month
- **Linode/Akamai**: Alternative - Similar to DigitalOcean
- **OVH**: Alternative - European provider, competitive pricing
- **On-premises**: Full control, requires infrastructure management

---

*Document Version: 4.0*  
*Last Updated: 2026-01-16*  
*Author: Architecture Team*  
*Updated: Added Issue #13 (Location Data Polling), removed alternative architecture options*
