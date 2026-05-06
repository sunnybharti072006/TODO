package com.list.to_do_list

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID


@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val priority: String = "MEDIUM",   // stored as string name
    val isCompleted: Boolean = false
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY rowid ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

// ─────────────────────────────────────────────
// ROOM — DATABASE
// ─────────────────────────────────────────────

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: TodoDatabase? = null

        fun getDatabase(context: Context): TodoDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TodoDatabase::class.java,
                    "todo_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

// ─────────────────────────────────────────────
// DATA MODEL (UI layer)
// ─────────────────────────────────────────────

enum class Priority(val label: String, val color: Color) {
    LOW("Low", Color(0xFF4CAF50)),
    MEDIUM("Medium", Color(0xFFFF9800)),
    HIGH("High", Color(0xFFF44336))
}

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val priority: Priority = Priority.MEDIUM,
    val isCompleted: Boolean = false
)

// Mappers
fun TaskEntity.toTask() = Task(
    id = id,
    title = title,
    priority = Priority.valueOf(priority),
    isCompleted = isCompleted
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    priority = priority.name,
    isCompleted = isCompleted
)

// ─────────────────────────────────────────────
// VIEWMODEL
// ─────────────────────────────────────────────

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = TodoDatabase.getDatabase(application).taskDao()

    // Room Flow auto-updates UI whenever DB changes
    val tasks: StateFlow<List<Task>> = dao.getAllTasks()
        .map { list -> list.map { it.toTask() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun addTask(title: String, priority: Priority) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertTask(Task(title = title.trim(), priority = priority).toEntity())
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTask(task.toEntity())
        }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch {
            dao.updateTask(task.copy(isCompleted = !task.isCompleted).toEntity())
        }
    }
}

// ─────────────────────────────────────────────
// THEME
// ─────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF4285F4),
    tertiary = Color(0xFF34A853),
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3F4),
    onSurface = Color(0xFF202124),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDEE1E6),
    error = Color(0xFFEA4335)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF003064),
    primaryContainer = Color(0xFF00468B),
    onPrimaryContainer = Color(0xFFD2E4FF),
    secondary = Color(0xFF669DF6),
    tertiary = Color(0xFF81C995),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF2D2D2D),
    surfaceVariant = Color(0xFF3C3C3C),
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF49454F),
    error = Color(0xFFF28B82)
)

@Composable
fun TodoAppTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
        ),
        content = content
    )
}

// ─────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: TaskViewModel = viewModel()
            val isDark by vm.isDarkMode.collectAsState()
            TodoAppTheme(darkTheme = isDark) {
                TodoScreen(vm)
            }
        }
    }
}

// ─────────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(vm: TaskViewModel) {
    val tasks by vm.tasks.collectAsState()
    val isDark by vm.isDarkMode.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }

    val filters = listOf("All", "Active", "Done")
    val filteredTasks = when (selectedFilter) {
        "Active" -> tasks.filter { !it.isCompleted }
        "Done"   -> tasks.filter { it.isCompleted }
        else     -> tasks
    }

    val completedCount = tasks.count { it.isCompleted }
    val progress = if (tasks.isEmpty()) 0f else completedCount.toFloat() / tasks.size

    Scaffold(
        topBar = {
            TopAppBarSection(isDark = isDark, onToggleDark = vm::toggleDarkMode)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New Task") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ProgressCard(completed = completedCount, total = tasks.size, progress = progress)
            }
            item {
                FilterRow(filters = filters, selected = selectedFilter, onSelect = { selectedFilter = it })
            }
            if (filteredTasks.isEmpty()) {
                item { EmptyState(filter = selectedFilter) }
            } else {
                items(items = filteredTasks, key = { it.id }) { task ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(300))
                                + fadeIn(animationSpec = tween(300))
                    ) {
                        TaskCard(
                            task = task,
                            onToggle = { vm.toggleComplete(task) },
                            onDelete = { vm.deleteTask(task) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, priority ->
                vm.addTask(title, priority)
                showAddDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────
// TOP APP BAR
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarSection(isDark: Boolean, onToggleDark: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(text = "My Tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(text = "Stay organized", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        actions = {
            IconButton(onClick = onToggleDark) {
                Icon(
                    imageVector = if (isDark) Icons.Outlined.WbSunny else Icons.Outlined.DarkMode,
                    contentDescription = "Toggle dark mode",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

// ─────────────────────────────────────────────
// PROGRESS CARD
// ─────────────────────────────────────────────

@Composable
fun ProgressCard(completed: Int, total: Int, progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = EaseInOutCubic),
        label = "progress"
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "Progress", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(text = "$completed of $total done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Box(
                    modifier = Modifier.size(56.dp).background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "${(animatedProgress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        }
    }
}

// ─────────────────────────────────────────────
// FILTER ROW
// ─────────────────────────────────────────────

@Composable
fun FilterRow(filters: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

// ─────────────────────────────────────────────
// TASK CARD
// ─────────────────────────────────────────────

@Composable
fun TaskCard(task: Task, onToggle: () -> Unit, onDelete: () -> Unit) {
    val priorityColor = task.priority.color
    val scale by animateFloatAsState(targetValue = 1f, animationSpec = spring(dampingRatio = 0.6f), label = "scale")

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().scale(scale),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (task.isCompleted) 0.dp else 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.width(5.dp).height(72.dp).background(
                    color = if (task.isCompleted) priorityColor.copy(alpha = 0.3f) else priorityColor,
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                )
            )
            Spacer(Modifier.width(14.dp))
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (!task.isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = priorityColor.copy(alpha = if (task.isCompleted) 0.1f else 0.15f),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = task.priority.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.isCompleted) priorityColor.copy(alpha = 0.5f) else priorityColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

// ─────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────

@Composable
fun EmptyState(filter: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = when (filter) {
                "Done"   -> Icons.Rounded.CheckCircle
                "Active" -> Icons.Rounded.RadioButtonUnchecked
                else     -> Icons.Rounded.AssignmentLate
            },
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
        Text(
            text = when (filter) {
                "Done"   -> "Nothing completed yet"
                "Active" -> "All tasks done! 🎉"
                else     -> "No tasks yet"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        if (filter == "All") {
            Text(text = "Tap + to add your first task", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

// ─────────────────────────────────────────────
// ADD TASK DIALOG
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, Priority) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(Priority.MEDIUM) }
    val isValid = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("New Task", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task title") },
                    placeholder = { Text("What needs to be done?") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text("Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { priority ->
                        val isSelected = priority == selectedPriority
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPriority = priority },
                            label = { Text(priority.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = priority.color,
                                selectedLabelColor = Color.White,
                                labelColor = priority.color
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = priority.color.copy(alpha = 0.5f),
                                selectedBorderColor = priority.color
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) onAdd(title, selectedPriority) },
                enabled = isValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Task", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
