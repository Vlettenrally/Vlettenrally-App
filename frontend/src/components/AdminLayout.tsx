/**
 * Admin Layout Component - Contains navigation and layout structure for admin interface
 * Features responsive sidebar: drawer on mobile, persistent collapsible sidebar on desktop
 */
import { useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  AppBar,
  Box,
  Toolbar,
  Typography,
  IconButton,
  Drawer,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemButton,
  Divider,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  useMediaQuery,
  useTheme,
  Tooltip,
} from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import {
  Menu as MenuIcon,
  Dashboard as DashboardIcon,
  SportsScore as GamesIcon,
  Groups as TeamsIcon,
  Message as MessageIcon,
  AutoFixHigh as AutomationsIcon,
  Analytics as AnalyticsIcon,
  Logout as LogoutIcon,
  AdminPanelSettings as AdminIcon,
  ChevronLeft as CollapseIcon,
  ChevronRight as ExpandIcon,
} from '@mui/icons-material';
import { storage } from '../services/storage';
import {
  ActiveGamesWidget,
  CheckInProgressWidget,
  GameClockWidget,
  SpeedAlertsWidget,
} from './widgets';

const SIDEBAR_WIDTH = 280;
const SIDEBAR_COLLAPSED_WIDTH = 64;

interface AdminLayoutProps {
  children: ReactNode;
}

interface Game {
  id: string;
  name: string;
}

export function AdminLayout({ children }: AdminLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
  
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [games, setGames] = useState<Game[]>([]);
  const [selectedGameId, setSelectedGameId] = useState<string>('');

  // Load games and selected game on mount
  useEffect(() => {
    const loadGames = async () => {
      try {
        const token = storage.getAuthToken();
        const response = await fetch('/api/v1/games', {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          const data = await response.json();
          setGames(data);
          
          // Set selected game from storage or default to first game
          const storedGameId = storage.getAdminSelectedGame();
          if (storedGameId && data.some((g: Game) => g.id === storedGameId)) {
            setSelectedGameId(storedGameId);
          } else if (data.length > 0) {
            setSelectedGameId(data[0].id);
            storage.setAdminSelectedGame(data[0].id);
          }
        }
      } catch (error) {
        console.error('Failed to load games:', error);
      }
    };
    loadGames();
  }, []);

  const menuItems = [
    { text: 'Dashboard', icon: <DashboardIcon />, path: '/admin' },
    { text: 'Games', icon: <GamesIcon />, path: '/admin/games' },
    { text: 'Teams', icon: <TeamsIcon />, path: '/admin/teams' },
    { text: 'Berichten', icon: <MessageIcon />, path: '/admin/messages' },
    { text: 'Automations', icon: <AutomationsIcon />, path: '/admin/automations' },
    { text: 'Analytics', icon: <AnalyticsIcon />, path: '/admin/analytics' },
  ];

  const handleNavigate = (path: string) => {
    navigate(path);
    setDrawerOpen(false);
  };

  const handleLogout = () => {
    storage.clearSession();
    navigate('/admin/login');
  };

  const handleGameChange = (event: SelectChangeEvent) => {
    const gameId = event.target.value;
    setSelectedGameId(gameId);
    storage.setAdminSelectedGame(gameId);
  };

  const sidebarWidth = sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_WIDTH;

  // Sidebar content - shared between desktop sidebar and mobile drawer
  const sidebarContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box sx={{ p: 2, bgcolor: '#1a237e', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {!sidebarCollapsed && (
          <Typography variant="h6">Admin Panel</Typography>
        )}
        {isDesktop && (
          <IconButton
            size="small"
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            sx={{ color: 'white', ml: sidebarCollapsed ? 'auto' : 0, mr: sidebarCollapsed ? 'auto' : 0 }}
          >
            {sidebarCollapsed ? <ExpandIcon /> : <CollapseIcon />}
          </IconButton>
        )}
      </Box>
      <List>
        {menuItems.map((item) => (
          <ListItem key={item.path} disablePadding>
            <Tooltip title={sidebarCollapsed ? item.text : ''} placement="right">
              <ListItemButton
                selected={location.pathname === item.path}
                onClick={() => handleNavigate(item.path)}
                sx={{ justifyContent: sidebarCollapsed ? 'center' : 'flex-start', px: sidebarCollapsed ? 1 : 2 }}
              >
                <ListItemIcon sx={{ minWidth: sidebarCollapsed ? 0 : 40, justifyContent: 'center' }}>
                  {item.icon}
                </ListItemIcon>
                {!sidebarCollapsed && <ListItemText primary={item.text} />}
              </ListItemButton>
            </Tooltip>
          </ListItem>
        ))}
      </List>
      <Divider />
      
      {/* Widgets section - only shown on desktop when not collapsed */}
      {isDesktop && !sidebarCollapsed && (
        <Box sx={{ p: 2, flexGrow: 1, overflowY: 'auto' }}>
          <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
            Quick Stats
          </Typography>
          <GameClockWidget collapsed={sidebarCollapsed} />
          <ActiveGamesWidget collapsed={sidebarCollapsed} />
          <CheckInProgressWidget collapsed={sidebarCollapsed} />
          <SpeedAlertsWidget collapsed={sidebarCollapsed} />
        </Box>
      )}
      
      {/* Collapsed widgets - icon only */}
      {isDesktop && sidebarCollapsed && (
        <Box sx={{ py: 2, flexGrow: 1 }}>
          <GameClockWidget collapsed />
          <ActiveGamesWidget collapsed />
          <CheckInProgressWidget collapsed />
          <SpeedAlertsWidget collapsed />
        </Box>
      )}
      
      <Box sx={{ mt: 'auto' }}>
        <Divider />
        <List>
          <ListItem disablePadding>
            <Tooltip title={sidebarCollapsed ? 'Uitloggen' : ''} placement="right">
              <ListItemButton 
                onClick={handleLogout}
                sx={{ justifyContent: sidebarCollapsed ? 'center' : 'flex-start', px: sidebarCollapsed ? 1 : 2 }}
              >
                <ListItemIcon sx={{ minWidth: sidebarCollapsed ? 0 : 40, justifyContent: 'center' }}>
                  <LogoutIcon />
                </ListItemIcon>
                {!sidebarCollapsed && <ListItemText primary="Uitloggen" />}
              </ListItemButton>
            </Tooltip>
          </ListItem>
        </List>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Desktop persistent sidebar */}
      {isDesktop && (
        <Box
          component="nav"
          sx={{
            width: sidebarWidth,
            flexShrink: 0,
            transition: theme.transitions.create('width', {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.enteringScreen,
            }),
          }}
        >
          <Box
            sx={{
              width: sidebarWidth,
              height: '100vh',
              position: 'fixed',
              borderRight: 1,
              borderColor: 'divider',
              bgcolor: 'background.paper',
              transition: theme.transitions.create('width', {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.enteringScreen,
              }),
              overflowX: 'hidden',
            }}
          >
            {sidebarContent}
          </Box>
        </Box>
      )}

      {/* Mobile drawer */}
      {!isDesktop && (
        <Drawer anchor="left" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
          <Box sx={{ width: 250 }} role="presentation">
            {sidebarContent}
          </Box>
        </Drawer>
      )}

      {/* Main content area */}
      <Box sx={{ display: 'flex', flexDirection: 'column', flexGrow: 1, minWidth: 0 }}>
        <AppBar position="sticky" sx={{ bgcolor: '#1a237e' }}>
          <Toolbar>
            {/* Only show hamburger on mobile */}
            {!isDesktop && (
              <IconButton
                size="large"
                edge="start"
                color="inherit"
                aria-label="menu"
                sx={{ mr: 2 }}
                onClick={() => setDrawerOpen(true)}
              >
                <MenuIcon />
              </IconButton>
            )}
            <AdminIcon sx={{ mr: 1 }} />
            <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
              Vlettenrally Admin
            </Typography>
            {games.length > 0 && (
              <FormControl size="small" sx={{ minWidth: 150, mr: 2 }}>
                <InputLabel id="game-select-label" sx={{ color: 'white' }}>Game</InputLabel>
                <Select
                  labelId="game-select-label"
                  value={selectedGameId}
                  label="Game"
                  onChange={handleGameChange}
                  sx={{
                    color: 'white',
                    '.MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.5)' },
                    '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: 'white' },
                    '.MuiSvgIcon-root': { color: 'white' },
                  }}
                >
                  {games.map((game) => (
                    <MenuItem key={game.id} value={game.id}>
                      {game.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            )}
          </Toolbar>
        </AppBar>

        <Box component="main" sx={{ flexGrow: 1, bgcolor: 'background.default', p: 0 }}>
          {children}
        </Box>
      </Box>
    </Box>
  );
}
