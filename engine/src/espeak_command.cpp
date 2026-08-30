/***************************************************************************
 *   Copyright (C) 2007, Gilles Casse <gcasse@oralux.org>                  *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 ***************************************************************************/
#include "speech.h"

#ifdef USE_ASYNC
// This source file is only used for asynchronious modes

#include "espeak_command.h"
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <wchar.h>

#include "debug.h"


static unsigned int my_current_text_id=0;


//<create_espeak_text
t_espeak_command* create_espeak_text(const void *text, size_t size, unsigned int position, espeak_POSITION_TYPE position_type, unsigned int end_position, unsigned int flags, void* user_data)
{
  ENTER("create_espeak_text");
  int a_error=1;
  void* a_text = NULL;
  t_espeak_text* data = NULL;
  t_espeak_command* a_command = (t_espeak_command*)malloc(sizeof(t_espeak_command));

  if (!text || !size || !a_command)
    {
      goto text_error;
    }
 
  a_text = malloc( size );
  if (!a_text)
    {
      goto text_error;
    }
  memcpy(a_text, text, size);
  
  a_command->type = ET_TEXT;
  a_command->state = CS_UNDEFINED;
  data = &(a_command->u.my_text);
  data->unique_identifier = ++my_current_text_id;
  data->text = a_text;
  data->size = size;
  data->position = position;
  data->position_type = position_type;
  data->end_position = end_position;
  data->flags = flags;
  data->user_data = user_data;
  a_error=0;

  SHOW("ET_TEXT malloc text=%p, command=%p (uid=%d)\n", (void*)a_text, (void*)a_command, data->unique_identifier);

 text_error:
  if (a_error)
    {
      if (a_text)
	{
	  free (a_text);
	}
      if (a_command)
	{
	  free (a_command);
	}
      a_command = NULL;
    }

  SHOW("command=%p\n", (void*)a_command);

  return a_command;
}

//>


t_espeak_command* create_espeak_terminated_msg(unsigned int unique_identifier, void* user_data)
{
  ENTER("create_espeak_terminated_msg");
  int a_error=1;
  t_espeak_terminated_msg* data = NULL;
  t_espeak_command* a_command = (t_espeak_command*)malloc(sizeof(t_espeak_command));

  if (!a_command)
    {
      goto msg_error;
    }
   
  a_command->type = ET_TERMINATED_MSG;
  a_command->state = CS_UNDEFINED;
  data = &(a_command->u.my_terminated_msg);
  data->unique_identifier = unique_identifier;
  data->user_data = user_data;
  a_error=0;

  SHOW("ET_TERMINATED_MSG command=%p (uid=%d, user_data=%p)\n", (void*)a_command, unique_identifier, (void*)user_data);

 msg_error:
  if (a_error)
    {
      if (a_command)
	{
	  free (a_command);
	}
      a_command = NULL;
    }

  SHOW("command=%p\n", (void*)a_command);

  return a_command;

}




//<create_espeak_mark
t_espeak_command* create_espeak_mark(const void *text, size_t size, const char *index_mark, unsigned int end_position, unsigned int flags, void* user_data)
{
  ENTER("create_espeak_mark");
  int a_error=1;
  void* a_text = NULL;
  char *a_index_mark = NULL;
  t_espeak_mark* data = NULL;
  t_espeak_command* a_command = (t_espeak_command*)malloc(sizeof(t_espeak_command));

  if (!text || !size || !index_mark || !a_command)
    {
      goto mark_error;
    }

  a_text = malloc( size );
  if (!a_text)
    {
      goto mark_error;
    }
  memcpy(a_text, text, size);

  a_index_mark = strdup( index_mark);
	if (!a_index_mark)
	  {
	    goto mark_error;
	  }

  a_command->type = ET_MARK;
  a_command->state = CS_UNDEFINED;
  data = &(a_command->u.my_mark);
  data->unique_identifier = ++my_current_text_id;
  data->text = a_text;
  data->size = size;
  data->index_mark = a_index_mark;
  data->end_position = end_position;
  data->flags = flags;
  data->user_data = user_data;
  a_error=0;

 mark_error:
  if (a_error)
    {
      if (a_text)
	{
	  free (a_text);
	}
      if (a_command)
	{
	  free (a_command);
	}
      a_command = NULL;
      if (a_index_mark)
	{
	  free (a_index_mark);
	}
    }

	if (data)
	  {
	    SHOW("ET_MARK malloc text=%p, command=%p (uid=%d)\n", (void*)a_text, (void*)a_command, data->unique_identifier);
	  }

  return a_command;
}
//>
//< create_espeak_key, create_espeak_char

t_espeak_command* create_espeak_key(const char *key_name, void *user_data)
{
  ENTER("create_espeak_key");
  int a_error=1;
  t_espeak_command* a_command = (t_espeak_command*)malloc(sizeof(t_espeak_command));

  if (!key_name || !a_command)
    {
      goto key_error;
    }

  a_command->type = ET_KEY;
  a_command->state = CS_UNDEFINED;
  a_command->u.my_key.user_data = user_data;
  a_command->u.my_key.unique_identifier = ++my_current_text_id;
  a_command->u.my_key.key_name = strdup( key_name);
	if (!a_command->u.my_key.key_name)
	  {
	    goto key_error;
	  }
  a_error=0;

 key_error:
  if (a_error)
    {
      if (a_command)
	{
	  free (a_command);
	}
      a_command = NULL;
    }

  SHOW("command=%p\n", (void*)a_command);

  return a_command;
}

t_espeak_command* create_espeak_char(wchar_t character, void* user_data)
{
  ENTER("create_espeak_char");
  int a_error=1;
  t_espeak_command* a_command = (t_espeak_command*)malloc(sizeof(t_espeak_command));
  if (!a_command)
    {
      goto char_error;
    }
 
  a_command->type = ET_CHAR;
  a_command->state = CS_UNDEFINED;
  a_command->u.my_char.user_data = user_data;
  a_command->u.my_char.unique_identifier = ++my_current_text_id;
  a_command->u.my_char.character = character;
  a_error=0;

 char_error:
  if (a_error)
    {
      if (a_command)
	{
	  free (a_command);
	}
      a_command = NULL;
    }

  SHOW("command=%p\n", (void*)a_command);

  return a_command;
}

//>
//< create_espeak_parameter

t_espeak_command* create_espeak_parameter(espeak_PARAMETER parameter, int value, int relative)
{
  ENTER("create_espeak_parameter");
  int a_error=1;
  t_espeak_parameter* data = NULL;
  t_espeak_command* a_command = (t_espeak_command*)malloc(sizeof(t_espeak_command));
  if (!a_command)
    {
      goto param_error;
    }
 
  a_command->type = ET_PARAMETER;
  a_command->state = CS_UNDEFINED;
  data = &(a_command->u.my_param);
  data->parameter = parameter; 
  data->value = value;
  data->relative = relative;
  a_error=0;

 param_error:
  if (a_error)
    {
      if (a_command)
	{
	  free (a_command);
	}
      a_command = NULL;
    }

  SHOW("command=%p\n", (void*)a_command);

  return a_command;
}

//>
//< create_espeak_punctuation_list

t_espeak_command* create_espeak_punctuation_list(const wchar_t *punctlist)
{
  ENTER("create_espeak_punctuation_list");
  int a_error=1;
	wchar_t *a_list = NULL;
  t_espeak_command* a_command = (t_espeak_command*)malloc(sizeof(t_espeak_command));

  if (!punctlist || !a_command)
    {
      goto list_error;
    }
 
  a_command->type = ET_PUNCTUATION_LIST;
  a_command->state = CS_UNDEFINED;

  {
	size_t n_characters = wcslen(punctlist);
	if (n_characters >= (((size_t)-1)/sizeof(wchar_t)))
	  {
	    goto list_error;
	  }
	size_t len = (n_characters + 1)*sizeof(wchar_t);
	a_list = (wchar_t*)malloc(len);
	if (!a_list)
	  {
	    goto list_error;
	  }
    memcpy(a_list, punctlist, len);
    a_command->u.my_punctuation_list = a_list;
  }

  a_error=0;

 list_error:
  if (a_error)
    {
	  if (a_list)
	{
	  free(a_list);
	}
      if (a_command)
	{
	  free (a_command);
	}
      a_command = NULL;
    }

  SHOW("command=%p\n", (void*)a_command);

  return a_command;
}

//>
//< create_espeak_voice_name, create_espeak_voice_spec

t_espeak_command* create_espeak_voice_name(const char *name)
{
  ENTER("create_espeak_voice_name");

  int a_error=1;
  t_espeak_command* a_command = (t_espeak_command*)malloc(sizeof(t_espeak_command));

  if (!name || !a_command)
    {
      goto name_error;
    }
 
  a_command->type = ET_VOICE_NAME;
  a_command->state = CS_UNDEFINED;
  a_command->u.my_voice_name = strdup( name);
	if (!a_command->u.my_voice_name)
	  {
	    goto name_error;
	  }
  a_error=0;

 name_error:
  if (a_error)
    {
      if (a_command)
	{
	  free (a_command);
	}
      a_command = NULL;
    }

  SHOW("command=%p\n", (void*)a_command);

  return a_command;
}

t_espeak_command* create_espeak_voice_spec(espeak_VOICE *voice)
{
  ENTER("create_espeak_voice_spec");
  int a_error=1;
	espeak_VOICE* data = NULL;
  t_espeak_command* a_command = (t_espeak_command*)malloc(sizeof(t_espeak_command));

  if (!voice || !a_command)
    {
      goto spec_error;
    }
 
  a_command->type = ET_VOICE_SPEC;
  a_command->state = CS_UNDEFINED;
  {
	data = &(a_command->u.my_voice_spec);
    memcpy(data, voice, sizeof(espeak_VOICE));
	data->name = NULL;
	data->languages = NULL;
	data->identifier = NULL;

    if (voice->name)
      {
	data->name = strdup(voice->name);
	if (!data->name)
	  goto spec_error;
      }

    if (voice->languages)
      {
	data->languages = strdup(voice->languages);
	if (!data->languages)
	  goto spec_error;
      }

    if (voice->identifier)
      {
	data->identifier = strdup(voice->identifier);
	if (!data->identifier)
	  goto spec_error;
      }

    a_error=0;
  }

 spec_error:
  if (a_error)
    {
	  if (data)
	{
	  if (data->name) free((void *)data->name);
	  if (data->languages) free((void *)data->languages);
	  if (data->identifier) free((void *)data->identifier);
	}
      if (a_command)
	{
	  free (a_command);
	}
      a_command = NULL;
    }

  SHOW("command=%p\n", (void*)a_command);

  return a_command;
}

//>
//< delete_espeak_command
int delete_espeak_command( t_espeak_command* the_command)
{
  ENTER("delete_espeak_command");
  int a_status = 0;
  if (the_command)
    {
      switch(the_command->type)
	{
	case ET_TEXT:
	  if (the_command->u.my_text.text)
	    {
	      SHOW("delete_espeak_command > ET_TEXT free text=%p, command=%p, uid=%d\n", (void*)the_command->u.my_text.text, (void*)the_command, the_command->u.my_text.unique_identifier);
	      free(the_command->u.my_text.text);
	    }
	  break;

	case ET_MARK:
	  if (the_command->u.my_mark.text)
	    {
	      free(the_command->u.my_mark.text);
	    }
	  if (the_command->u.my_mark.index_mark)
	    {
	      free((void*)(the_command->u.my_mark.index_mark));
	    }
	  break;

	case ET_TERMINATED_MSG:
	  { 
	    // if the terminated msg is pending,
	    // it must be processed here for informing the calling program 
	    // that its message is finished.
	    // This can be important for cleaning the related user data.	    
	    t_espeak_terminated_msg* data = &(the_command->u.my_terminated_msg);
	    if (the_command->state == CS_PENDING)
	      {
		the_command->state = CS_PROCESSED;
		SHOW("delete_espeak_command > ET_TERMINATED_MSG callback (command=%p, uid=%d) \n", (void*)the_command, data->unique_identifier);
		sync_espeak_terminated_msg( data->unique_identifier, data->user_data);
	      }
	  }
	  break;

	case ET_KEY:
	  if (the_command->u.my_key.key_name)
	    {
	      free((void*)(the_command->u.my_key.key_name));
	    }
	  break;

	case ET_CHAR:
	case ET_PARAMETER:
	  // No allocation
	  break;

	case ET_PUNCTUATION_LIST:
	  if (the_command->u.my_punctuation_list)
	    {
	      free((void*)(the_command->u.my_punctuation_list));
	    }
	  break;

	case ET_VOICE_NAME:
	  if (the_command->u.my_voice_name)
	  {
	    free((void*)(the_command->u.my_voice_name));
	  }
	  break;
	  
	case ET_VOICE_SPEC:
	  {
		espeak_VOICE* data = &(the_command->u.my_voice_spec);

		if (data->name)
		{
			free((void *)data->name);
		}

		if (data->languages)
		{
			free((void *)data->languages);
		}

		if (data->identifier)
		{
			free((void *)data->identifier);
		}
	  }
	  break;

	default:
	  assert(0);
	}
      SHOW("delete_espeak_command > free command=%p\n", (void*)the_command);
      free(the_command);
      a_status = 1;
    }
  return a_status;
}
//>
//< process_espeak_command
void process_espeak_command( t_espeak_command* the_command)
{
  ENTER("process_espeak_command");

  SHOW("command=%p\n", (void*)the_command);

  if (the_command == NULL)
    {
      return;
    }

  the_command->state = CS_PROCESSED;

  switch(the_command->type)
    {
    case ET_TEXT:
      {
	t_espeak_text* data = &(the_command->u.my_text);
	sync_espeak_Synth( data->unique_identifier, data->text, data->size, 
			   data->position, data->position_type, 
			   data->end_position, data->flags, data->user_data);	
      }
      break;

    case ET_MARK:
      {
	t_espeak_mark* data = &(the_command->u.my_mark);
	sync_espeak_Synth_Mark( data->unique_identifier, data->text, data->size, 
				data->index_mark, data->end_position, data->flags, 
				data->user_data);
      }
      break;

    case ET_TERMINATED_MSG:
      { 
	t_espeak_terminated_msg* data = &(the_command->u.my_terminated_msg);
	sync_espeak_terminated_msg( data->unique_identifier, data->user_data);
      }
      break;

    case ET_KEY:
      {
	const char* data = the_command->u.my_key.key_name;
	sync_espeak_Key(data);
      }
      break;

    case ET_CHAR:
      {
	const wchar_t data = the_command->u.my_char.character;
	sync_espeak_Char( data);
      }
      break;

    case ET_PARAMETER:
      {
	t_espeak_parameter* data = &(the_command->u.my_param);
	SetParameter( data->parameter, data->value, data->relative);
      }
      break;

    case ET_PUNCTUATION_LIST:
      {
	const wchar_t* data = the_command->u.my_punctuation_list;
	sync_espeak_SetPunctuationList( data);
      }
      break;

    case ET_VOICE_NAME:
      {
	const char* data = the_command->u.my_voice_name;
	SetVoiceByName( data);
      }
      break;

    case ET_VOICE_SPEC:
      {
	espeak_VOICE* data = &(the_command->u.my_voice_spec);
	SetVoiceByProperties(data);
      }
      break;

    default:
      assert(0);
      break;
    }
}

//>

//< process_espeak_command
void display_espeak_command( t_espeak_command* the_command)
{
  ENTER("display_espeak_command");
#ifdef DEBUG_ENABLED
  if (the_command == NULL)
    {
      SHOW("display_espeak_command > command=%s\n","NULL");
      return;
    }

  SHOW("display_espeak_command > state=%d\n",the_command->state);

  switch(the_command->type)
    {
    case ET_TEXT:
      {
	t_espeak_text* data = &(the_command->u.my_text);
	SHOW("display_espeak_command > (%p) uid=%d, TEXT=%s, user_data=%p\n", (void*)the_command, data->unique_identifier, (char*)data->text, (void*)(data->user_data));
      }
      break;

    case ET_MARK:
      {
	t_espeak_mark* data = &(the_command->u.my_mark);
	SHOW("display_espeak_command > (%p) uid=%d, MARK=%s, user_data=%p\n", (void*)the_command, data->unique_identifier, (char*)data->text, (void*)(data->user_data));
      }
      break;

    case ET_KEY:
      {
	const char* data = the_command->u.my_key.key_name;
	SHOW("display_espeak_command > (%p) KEY=%s\n", (void*)the_command, data);
      }
      break;

    case ET_TERMINATED_MSG:
      {
	t_espeak_terminated_msg* data = &(the_command->u.my_terminated_msg);

	SHOW("display_espeak_command > (%p) TERMINATED_MSG uid=%d, user_data=%p, state=%d\n", 
	     (void*)the_command, data->unique_identifier, (void*)data->user_data, 
	     the_command->state);
      }
      break;

    case ET_CHAR:
      {
	const wchar_t data = the_command->u.my_char.character;
	SHOW("display_espeak_command > (%p) CHAR=%c\n", (void*)the_command, (char)data);
      }
      break;

    case ET_PARAMETER:
      {
	t_espeak_parameter* data = &(the_command->u.my_param);
	SHOW("display_espeak_command > (%p) PARAMETER=%d, value=%d, relative=%d\n", 
	     (void*)the_command, data->parameter, data->value, data->relative);
      }
      break;

    case ET_PUNCTUATION_LIST:
      {
	const wchar_t* data = the_command->u.my_punctuation_list;
	sync_espeak_SetPunctuationList( data);
	SHOW("display_espeak_command > (%p) PUNCTLIST=%ls\n", (void*)the_command, data);
      }
      break;

    case ET_VOICE_NAME:
      {
	const char* data = the_command->u.my_voice_name;
	SHOW("display_espeak_command > (%p) VOICE_NAME=%s\n", (void*)the_command, data);
      }
      break;

    case ET_VOICE_SPEC:
      {
	SHOW("display_espeak_command > (%p) VOICE_SPEC", (void*)the_command);
      }
      break;

    default:
      assert(0);
      break;
    }
#endif
}

#endif
//>
